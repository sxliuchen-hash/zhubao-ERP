package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 子单对外视图，供母单详情和贴纸打印使用。
 */
public record ChildWorkOrderView(
        String code,
        String motherOrderCode,
        String verificationCode,
        String customerName,
        String processingRequirement,
        String dueDate,
        String goodsName,
        BigDecimal quantity,
        BigDecimal weight,
        List<String> processes,
        String qrContent
) {
    public ChildWorkOrderView {
        processes = processes == null ? List.of() : List.copyOf(processes);
    }
}
