package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交货完成判定。只描述当前事实，不写库存、进度或财务。
 * 有出货单、成品余额为零或进度 100% 都不能单独使 eligible 为真。
 */
public record OrderDeliveryAssessment(
        String targetType,
        String targetCode,
        String motherOrderCode,
        Long customerId,
        String customerName,
        boolean feeTarget,
        String deliveryState,
        boolean eligible,
        String deliveryRevision,
        List<DeliveryBlock> blocks,
        List<QuantityFact> quantities,
        List<LaborFact> laborFacts,
        List<ChildDelivery> children
) {
    public static final String MOTHER = "MOTHER";
    public static final String CHILD = "CHILD";
    public static final String NOT_COMPLETE = "NOT_COMPLETE";
    public static final String COMPLETE = "COMPLETE";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";

    public OrderDeliveryAssessment {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        quantities = quantities == null ? List.of() : List.copyOf(quantities);
        laborFacts = laborFacts == null ? List.of() : List.copyOf(laborFacts);
        children = children == null ? List.of() : List.copyOf(children);
        motherOrderCode = motherOrderCode == null ? "" : motherOrderCode;
        customerName = customerName == null ? "" : customerName;
        deliveryRevision = deliveryRevision == null ? "" : deliveryRevision;
    }

    /** 阻断原因。review 为真时进入交货待核，不能靠勾选少交完结。 */
    public record DeliveryBlock(String code, String message, boolean review) {
    }

    /** 某一材质的应交与净已发。role 为 TARGET 或 REMAINDER。 */
    public record QuantityFact(
            String role,
            String materialCode,
            String unit,
            BigDecimal required,
            BigDecimal netShipped
    ) {
        public static final String TARGET = "TARGET";
        public static final String REMAINDER = "REMAINDER";
    }

    /**
     * 待核对作业。operatorName 只是交工显示名，不是计酬账号。
     * stationClose 表示整道交齐登记，不能把该人当成唯一计酬人。
     * manualQuantity 为真时数量留空，由交货后人工填写。
     */
    public record LaborFact(
            String sourceRef,
            String orderCode,
            String processName,
            String batchCode,
            BigDecimal quantity,
            boolean weightOnly,
            boolean manualQuantity,
            boolean stationClose,
            String operatorName,
            BigDecimal measuredWeight
    ) {
        public LaborFact {
            sourceRef = sourceRef == null ? "" : sourceRef;
            orderCode = orderCode == null ? "" : orderCode;
            processName = processName == null ? "" : processName;
            batchCode = batchCode == null ? "" : batchCode;
            operatorName = operatorName == null ? "" : operatorName;
        }
    }

    /** 母单汇总时的子单交货结论。 */
    public record ChildDelivery(
            String code,
            boolean eligible,
            String deliveryState,
            String deliveryRevision,
            List<DeliveryBlock> blocks
    ) {
        public ChildDelivery {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }
}
