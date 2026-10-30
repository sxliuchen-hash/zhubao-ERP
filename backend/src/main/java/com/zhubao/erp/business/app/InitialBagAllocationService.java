package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 按已经存在的收货来源建立初始袋。不新增收货，也不把映射当成已经开工。
 */
public class InitialBagAllocationService {

    private final ActivityBagService bags;

    public InitialBagAllocationService(ActivityBagService bags) {
        this.bags = bags;
    }

    public List<ActivityBag> arrange(String targetCode, List<SourceLine> lines, WorkSession session) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("没有可分配的收货来源");
        }
        List<ActivityBag> existing = bags.activeLeaves(targetCode);
        if (!existing.isEmpty()) {
            return existing;
        }
        List<ActivityBag> created = new ArrayList<>();
        for (SourceLine line : lines) {
            created.add(bags.open(
                    targetCode, line.materialCode(), line.requirementVersion(), line.sourceKey(),
                    line.quantity(), line.weight(), session));
        }
        return List.copyOf(created);
    }

    public record SourceLine(
            String sourceKey,
            String materialCode,
            String requirementVersion,
            BigDecimal quantity,
            StockMeasurement.Weight weight
    ) {
    }
}
