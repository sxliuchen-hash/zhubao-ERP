package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 手机收货。母单号由系统生成，快递单号用来匹配已有母单。
 */
public record MobileIntakeCommand(
        @NotBlank(message = "快递单号不能为空") String courierNo,
        @NotBlank(message = "客户不能为空") String customerName,
        Long customerId,
        @NotBlank(message = "加工要求不能为空") String processingRequirement,
        @NotBlank(message = "完成期限不能为空") String dueDate,
        @NotBlank(message = "货品名称不能为空") String goodsName,
        @NotNull @DecimalMin(value = "0.000", inclusive = false, message = "数量必须大于 0") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.000", inclusive = false, message = "重量必须大于 0") BigDecimal weight,
        String expressCompany,
        String materialCode,
        String quantityUnit,
        String requestId
) {
    public MobileIntakeCommand(
            String courierNo,
            String customerName,
            Long customerId,
            String processingRequirement,
            String dueDate,
            String goodsName,
            BigDecimal quantity,
            BigDecimal weight,
            String expressCompany,
            String materialCode,
            String quantityUnit
    ) {
        this(courierNo, customerName, customerId, processingRequirement, dueDate, goodsName,
                quantity, weight, expressCompany, materialCode, quantityUnit, null);
    }
}
