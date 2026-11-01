package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.QuantityDispositionLine;
import com.zhubao.erp.business.domain.QuantityDispositionRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按已经存在的收货或领用来源建立初始袋。不新增收货，也不把映射当成已经开工。
 * 未关闭子袋加上已确认损耗和报废不能超过来源数量。关闭父袋不重复计算，退回可以再领。
 */
public class InitialBagAllocationService {

    private final ActivityBagService bags;
    private final SourceLimit limits;
    private final QuantityDispositionRepository dispositions;
    private final Object gate = new Object();

    public InitialBagAllocationService(ActivityBagService bags, SourceLimit limits) {
        this(bags, limits, null);
    }

    public InitialBagAllocationService(ActivityBagService bags, SourceLimit limits,
                                       QuantityDispositionRepository dispositions) {
        this.bags = bags;
        this.limits = limits;
        this.dispositions = dispositions;
    }

    public List<ActivityBag> arrange(String targetCode, List<SourceLine> lines, WorkSession session) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("没有可分配的收货来源");
        }
        boolean persistent = session != null && session.connection().isPresent();
        if (!persistent) {
            synchronized (gate) {
                return arrangeWithin(targetCode, lines, session);
            }
        }
        return arrangeWithin(targetCode, lines, session);
    }

    private List<ActivityBag> arrangeWithin(String targetCode, List<SourceLine> lines, WorkSession session) {
        List<String> keys = lines.stream().map(line -> requiredKey(line.sourceKey())).distinct().sorted().toList();
        if (session != null && session.connection().isPresent()) {
            for (String key : keys) {
                bags.lockSource(key, session);
            }
        }
        List<ActivityBag> existing = bags.activeLeaves(targetCode);
        if (!existing.isEmpty()) {
            return existing.stream()
                    .sorted(Comparator.comparing(ActivityBag::sourceKey).thenComparing(ActivityBag::id))
                    .toList();
        }
        Map<String, BigDecimal> requested = new LinkedHashMap<>();
        for (SourceLine line : lines) {
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new BusinessException("分配数量必须为正");
            }
            requested.merge(requiredKey(line.sourceKey()), line.quantity(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> entry : requested.entrySet()) {
            BigDecimal limit = limits.limit(entry.getKey());
            if (limit == null) {
                throw new BusinessException("来源不存在，不能分配");
            }
            BigDecimal used = occupied(entry.getKey());
            if (used.add(entry.getValue()).compareTo(limit) > 0) {
                throw new BusinessException("来源剩余不足");
            }
        }
        List<ActivityBag> created = new ArrayList<>();
        for (SourceLine line : lines) {
            created.add(bags.open(
                    targetCode, line.materialCode(), line.requirementVersion(), requiredKey(line.sourceKey()),
                    line.quantity(), line.weight(), session));
        }
        return List.copyOf(created);
    }

    private BigDecimal occupied(String sourceKey) {
        BigDecimal used = BigDecimal.ZERO;
        for (ActivityBag bag : bags.bagsOfSource(sourceKey)) {
            if (bag.activeLeaf()) {
                used = used.add(bag.quantity());
            }
            if (dispositions == null) {
                continue;
            }
            for (QuantityDispositionLine line : dispositions.listByBag(bag.id())) {
                used = used.add(amount(line.loss())).add(amount(line.scrap()));
            }
        }
        return used;
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String requiredKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new BusinessException("来源不存在，不能分配");
        }
        return sourceKey.trim();
    }

    /**
     * 收货或已领来源的真实数量。调用方读取原收货或领用，不能把本次想分配的数量当成额度。
     */
    public interface SourceLimit {
        BigDecimal limit(String sourceKey);
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
