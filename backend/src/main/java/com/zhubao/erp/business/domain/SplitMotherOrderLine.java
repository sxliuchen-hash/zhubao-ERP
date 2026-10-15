package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 拆出的一张子单。processes 为 null 时沿用雕刻、镶嵌、抛光；空列表表示全部勾掉。
 */
public record SplitMotherOrderLine(
        BigDecimal quantity,
        BigDecimal weight,
        List<String> processes,
        List<SplitSourceAllocation> allocations
) {
    public SplitMotherOrderLine {
        processes = processes == null ? null : List.copyOf(processes);
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }

    public SplitMotherOrderLine(BigDecimal quantity, BigDecimal weight, List<String> processes) {
        this(quantity, weight, processes, List.of());
    }
}
