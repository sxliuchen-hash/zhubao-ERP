package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次命令关闭父袋并建立子份额。打印失败后用原请求回放，不再分出新袋。
 */
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
        return unit.execute(new CommandCall(requestId, actorId, "BAG_SPLIT", "BAG", parentId, payload), session -> {
            ActivityBag parent = repository.find(parentId).orElseThrow(() -> new BusinessException("袋不存在"));
            if (parent.closed()) {
                throw new BusinessException("父袋已关闭，不能再次分袋");
            }
            BigDecimal sum = quantities.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
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
            return String.join(",", children);
        });
    }
}
