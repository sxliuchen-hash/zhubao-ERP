package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.NodePortionWriter;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionInspectionRepository;
import com.zhubao.erp.business.domain.ReworkTask;
import com.zhubao.erp.business.domain.ReworkTaskRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一次命令关闭父袋并建立子份额。子袋使用新的袋号，不在父号后追加序号。
 * 打印失败后用原请求回放，不再分出新袋。
 */
@Service
public class BagSplitService {

    private final com.zhubao.erp.business.domain.PortionReleaseRepository holds;
    private final ActivityBagRepository repository;
    private final ActivityBagService bags;
    private final BusinessWorkUnit unit;
    private final NodePortionReader portions;
    private final NodePortionWriter portionWriter;
    private final PortionInspectionRepository inspections;
    private final ReworkTaskRepository rework;

    public BagSplitService(ActivityBagRepository repository, ActivityBagService bags, BusinessWorkUnit unit) {
        this(repository, bags, unit, null, null);
    }

    public BagSplitService(ActivityBagRepository repository, ActivityBagService bags, BusinessWorkUnit unit,
                           NodePortionReader portions, NodePortionWriter portionWriter) {
        this(repository, bags, unit, portions, portionWriter, null, null);
    }

    public BagSplitService(ActivityBagRepository repository, ActivityBagService bags, BusinessWorkUnit unit,
                           NodePortionReader portions, NodePortionWriter portionWriter,
                           PortionInspectionRepository inspections, ReworkTaskRepository rework) {
        this(repository,bags,unit,portions,portionWriter,inspections,rework,null);
    }
    @Autowired
    public BagSplitService(ActivityBagRepository repository,ActivityBagService bags,BusinessWorkUnit unit,NodePortionReader portions,
            NodePortionWriter portionWriter,PortionInspectionRepository inspections,ReworkTaskRepository rework,
            com.zhubao.erp.business.domain.PortionReleaseRepository holds) {
        this.holds=holds;
        this.repository = repository;
        this.bags = bags;
        this.unit = unit;
        this.portions = portions;
        this.portionWriter = portionWriter;
        this.inspections = inspections;
        this.rework = rework;
    }

    public String split(String requestId, String actorId, String parentId, List<BigDecimal> quantities) {
        String payload = parentId + "|" + quantities;
        return unit.execute(new CommandCall(requestId, actorId, "BAG_SPLIT", "BAG", parentId, payload),
                session -> String.join(",", splitWithin(parentId, quantities, session)));
    }

    /**
     * 在调用方的工作单元里分袋。部分发外使用这一路径，避免再开一次命令。
     * 子袋不复制原袋重量。来源、目标和要求与父袋相同。
     */
    public List<String> splitWithin(String parentId, List<BigDecimal> quantities, WorkSession session) {
        ActivityBag parent = repository.lock(parentId, session).orElseThrow(() -> new BusinessException("袋不存在"));
        if (parent.closed()) {
            throw new BusinessException("父袋已关闭，不能再次分袋");
        }
        if (quantities == null || quantities.size() < 2) {
            throw new BusinessException("分袋至少分成两份");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal quantity : quantities) {
            if (quantity == null || quantity.signum() <= 0) {
                throw new BusinessException("分袋数量必须为正");
            }
            com.zhubao.erp.inventory.domain.StockMeasurement.processingScale(quantity);
            sum = sum.add(quantity);
        }
        if (sum.compareTo(parent.quantity()) != 0) {
            throw new BusinessException("分袋数量必须等于原袋数量");
        }
        List<String> children = new ArrayList<>();
        for (BigDecimal quantity : quantities) {
            children.add(bags.openShare(parent, quantity, session).id());
        }
        bags.close(parent.id(), session);
        copyPosition(parent, children, quantities, session);
        copyQuality(parent, children, quantities, session);
        if (holds!=null && holds.find(parent.id()).map(row -> row.frozen().signum()>0).orElse(false))
            for (int index=0;index<children.size();index++) holds.save(new com.zhubao.erp.business.domain.PortionRelease(children.get(index),BigDecimal.ZERO,quantities.get(index)),session);
        return children;
    }

    /**
     * 子袋沿用父袋已经到达和已经处理的份额，按数量拆开。
     * 不新开报工，也不把未开工的第一道数量再算一遍。
     */
    private void copyPosition(ActivityBag parent, List<String> childIds, List<BigDecimal> quantities, WorkSession session) {
        if (portions == null || portionWriter == null || parent.quantity().signum() == 0) {
            return;
        }
        for (NodePortionBalance row : portions.list(parent.id())) {
            BigDecimal arrivedLeft = row.arrived();
            BigDecimal handledLeft = row.handled();
            for (int index = 0; index < childIds.size(); index++) {
                boolean last = index == childIds.size() - 1;
                BigDecimal arrived = last ? arrivedLeft : share(row.arrived(), quantities.get(index), parent.quantity());
                BigDecimal handled = last ? handledLeft : share(row.handled(), quantities.get(index), parent.quantity());
                if (handled.compareTo(arrived) > 0) {
                    handled = arrived;
                }
                if (!last) {
                    arrivedLeft = arrivedLeft.subtract(arrived);
                    handledLeft = handledLeft.subtract(handled);
                }
                portionWriter.save(new NodePortionBalance(childIds.get(index), row.stepId(), arrived, handled), session);
            }
        }
    }

    /**
     * 质检和返工按子袋数量拆开。父袋保留原记录，子袋合计等于原数量，不把两层相加。
     */
    private void copyQuality(ActivityBag parent, List<String> childIds, List<BigDecimal> quantities, WorkSession session) {
        if (inspections != null) {
            for (PortionInspection row : inspections.listByBag(parent.id())) {
                BigDecimal passLeft = row.pass();
                BigDecimal reworkLeft = row.rework();
                BigDecimal scrapLeft = row.scrap();
                BigDecimal pendingLeft = row.pending();
                for (int index = 0; index < childIds.size(); index++) {
                    boolean last = index == childIds.size() - 1;
                    BigDecimal pass = last ? passLeft : share(row.pass(), quantities.get(index), parent.quantity());
                    BigDecimal rework = last ? reworkLeft : share(row.rework(), quantities.get(index), parent.quantity());
                    BigDecimal scrap = last ? scrapLeft : share(row.scrap(), quantities.get(index), parent.quantity());
                    BigDecimal pending = last ? pendingLeft : share(row.pending(), quantities.get(index), parent.quantity());
                    if (!last) {
                        passLeft = passLeft.subtract(pass);
                        reworkLeft = reworkLeft.subtract(rework);
                        scrapLeft = scrapLeft.subtract(scrap);
                        pendingLeft = pendingLeft.subtract(pending);
                    }
                    if (pass.signum() == 0 && rework.signum() == 0 && scrap.signum() == 0 && pending.signum() == 0) {
                        continue;
                    }
                    inspections.save(new PortionInspection(
                            newId("QC"), childRequest(row.requestId(), index + 1), childIds.get(index), row.requirementVersion(),
                            pass, rework, scrap, pending, row.failedClauses()), session);
                }
            }
        }
        if (rework == null) {
            return;
        }
        for (ReworkTask task : rework.listByReworkBag(parent.id())) {
            BigDecimal left = task.quantity();
            for (int index = 0; index < childIds.size(); index++) {
                boolean last = index == childIds.size() - 1;
                BigDecimal quantity = last ? left : share(task.quantity(), quantities.get(index), parent.quantity());
                if (!last) {
                    left = left.subtract(quantity);
                }
                if (quantity.signum() == 0) {
                    continue;
                }
                rework.save(new ReworkTask(
                        newId("RW"), childRequest(task.requestId(), index + 1), parent.id(), childIds.get(index),
                        task.targetCode(), task.requirementVersion(), task.round(), quantity), session);
            }
        }
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static String childRequest(String requestId, int index) {
        String text = requestId + "#" + index;
        return text.length() <= 64 ? text : newId("SR");
    }

    private static BigDecimal share(BigDecimal amount, BigDecimal child, BigDecimal parent) {
        return amount.multiply(child).divide(parent, 3, RoundingMode.DOWN);
    }
}
