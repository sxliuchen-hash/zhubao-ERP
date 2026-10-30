package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.app.OutsourcingDispatchService;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 从已有外协任务读取仍在外的数量。不写发外、收回或库存。未知重量不按颗数拆开。
 */
@Component
public class CustomerOwnedOutsideShares {

    private final OutsourcingSourceRepository sources;
    private final OutsourcingDispatchService custody;
    private final ActivityBagRepository bags;

    public CustomerOwnedOutsideShares(OutsourcingSourceRepository sources, OutsourcingDispatchService custody,
                                      ActivityBagRepository bags) {
        this.sources = sources;
        this.custody = custody;
        this.bags = bags;
    }

    public List<Share> shares(long customerId, Set<String> targetCodes) {
        if (targetCodes == null || targetCodes.isEmpty()) {
            return List.of();
        }
        String owner = Long.toString(customerId);
        List<Share> shares = new ArrayList<>();
        for (String target : targetCodes) {
            if (target == null || target.isBlank()) {
                continue;
            }
            for (OutsourcingTask task : sources.listByTarget(target)) {
                if (task.ownerType() != OwnerType.CUSTOMER || !owner.equals(task.ownerRef())) {
                    continue;
                }
                BigDecimal outside = custody.entrustedQuantity(task.taskId());
                if (outside.signum() <= 0) {
                    continue;
                }
                ActivityBag bag = bags.find(task.bagId()).orElse(null);
                String material = bag == null ? "" : bag.materialCode();
                StockMeasurement.Weight weight = bag == null || outside.compareTo(bag.quantity()) != 0
                        ? StockMeasurement.unknown()
                        : bag.weight();
                shares.add(new Share(task.targetCode(), material, outside, weight));
            }
        }
        return List.copyOf(shares);
    }

    public record Share(String targetCode, String materialCode, BigDecimal quantity, StockMeasurement.Weight weight) {
    }
}
