package com.zhubao.erp.business.app;

import java.math.BigDecimal;

public final class OrderMaintenanceCommands {
    private OrderMaintenanceCommands() {
    }

    public record MotherCorrection(Long customerId, String customerName, String materialCode, String goodsName,
                                   BigDecimal quantity, BigDecimal weight, String requirement, String dueDate,
                                   String remark, String reason) {
    }

    public record ChildCorrection(Long customerId, String customerName, String materialCode, String goodsName,
                                  BigDecimal quantity, BigDecimal weight, String requirement, String dueDate,
                                  String processes, String reason) {
    }

    public record PrintCorrection(int copies, String stationId, String queueId, String reason) {
    }

    public record RecycleCommand(String targetType, String code, String reason) {
    }

    public record RestoreCommand(String targetType, String code) {
    }
}
