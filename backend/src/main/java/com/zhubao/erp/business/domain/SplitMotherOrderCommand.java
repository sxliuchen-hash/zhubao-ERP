package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 把一张母单拆成多张子单。
 */
public record SplitMotherOrderCommand(
        String motherOrderCode,
        String operator,
        List<SplitMotherOrderLine> lines
) {
    public SplitMotherOrderCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
