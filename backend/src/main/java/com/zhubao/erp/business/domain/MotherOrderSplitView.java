package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 母单拆分详情：决定、剩余数量重量，以及已经拆出的子单。
 */
public record MotherOrderSplitView(
        String motherOrderCode,
        String decision,
        String decisionLabel,
        String verificationCode,
        String customerName,
        String processingRequirement,
        String dueDate,
        String goodsName,
        String quantityUnit,
        BigDecimal quantity,
        BigDecimal weight,
        BigDecimal remainingQuantity,
        BigDecimal remainingWeight,
        String qrContent,
        List<ChildWorkOrderView> children,
        List<ReceiptLineBalance> sourceLines
) {
    public MotherOrderSplitView {
        children = children == null ? List.of() : List.copyOf(children);
        sourceLines = sourceLines == null ? List.of() : List.copyOf(sourceLines);
    }

    public MotherOrderSplitView(String motherOrderCode, String decision, String decisionLabel,
            String verificationCode, String customerName, String processingRequirement, String dueDate,
            String goodsName, String quantityUnit, BigDecimal quantity, BigDecimal weight,
            BigDecimal remainingQuantity, BigDecimal remainingWeight, String qrContent,
            List<ChildWorkOrderView> children) {
        this(motherOrderCode, decision, decisionLabel, verificationCode, customerName, processingRequirement,
                dueDate, goodsName, quantityUnit, quantity, weight, remainingQuantity, remainingWeight,
                qrContent, children, List.of());
    }
}
