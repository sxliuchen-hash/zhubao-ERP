package com.zhubao.erp.business.app;

import java.math.BigDecimal;

public final class OrderMaintenanceCommands {
    private OrderMaintenanceCommands() {
    }

    public record MotherCorrection(Long customerId, String customerName, String materialCode, String goodsName,
                                   BigDecimal quantity, BigDecimal weight, String requirement, String dueDate,
                                   String remark, String reason, String requestId, String expectedRevision) {
        public MotherCorrection(Long customerId, String customerName, String materialCode, String goodsName,
                                BigDecimal quantity, BigDecimal weight, String requirement, String dueDate,
                                String remark, String reason) {
            this(customerId, customerName, materialCode, goodsName, quantity, weight, requirement, dueDate,
                    remark, reason, null, null);
        }
    }

    public record ChildCorrection(Long customerId, String customerName, String materialCode, String goodsName,
                                  BigDecimal quantity, BigDecimal weight, String requirement, String dueDate,
                                  String processes, String reason, String requestId, String expectedRevision) {
        public ChildCorrection(Long customerId, String customerName, String materialCode, String goodsName,
                               BigDecimal quantity, BigDecimal weight, String requirement, String dueDate,
                               String processes, String reason) {
            this(customerId, customerName, materialCode, goodsName, quantity, weight, requirement, dueDate,
                    processes, reason, null, null);
        }
    }

    public record PrintCorrection(int copies, String stationId, String queueId, String reason,
                                  String requestId, String expectedRevision) {
        public PrintCorrection(int copies, String stationId, String queueId, String reason) {
            this(copies, stationId, queueId, reason, null, null);
        }
    }

    public record RecycleCommand(String targetType, String code, String reason) {
    }

    public record RestoreCommand(String targetType, String code) {
    }
}
