package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次命令关闭父袋并建立子份额。打印失败后用原请求回放，不再分出新袋。
 */
@Service
public class BagSplitService {

    private final ActivityBagRepository repository;
    private final ActivityBagService bags;
    private final BusinessWorkUnit unit;

    public BagSplitService(ActivityBagRepository repository, ActivityBagService bags, BusinessWorkUnit unit) {
        this.repository = repository;
        this.bags = bags;
        this.unit = unit;
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
            sum = sum.add(quantity);
        }
        if (sum.compareTo(parent.quantity()) != 0) {
            throw new BusinessException("分袋数量必须等于原袋数量");
        }
        List<String> children = new ArrayList<>();
        for (BigDecimal quantity : quantities) {
            ActivityBag child = new ActivityBag(
                    parent.id() + "-" + (children.size() + 1), parent.targetCode(), parent.materialCode(),
                    parent.requirementVersion(), parent.sourceKey(), parent.id(), false, quantity,
                    StockMeasurement.unknown(), 1);
            repository.save(child, 0, session);
            children.add(child.id());
        }
        bags.close(parent.id(), session);
        return children;
    }
}
