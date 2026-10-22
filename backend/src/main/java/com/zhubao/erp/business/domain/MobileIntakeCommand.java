package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收货建单。母单号由系统生成，运单只记录来源。
 */
public record MobileIntakeCommand(
        String courierNo,
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
        String requestId,
        String sourceType,
        List<MobileIntakeLine> lines,
        String requirementTemplateCode,
        Integer requirementTemplateVersion
) {
    public MobileIntakeCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
        requirementTemplateCode = requirementTemplateCode == null ? "" : requirementTemplateCode.trim();
        requirementTemplateVersion = requirementTemplateVersion == null || requirementTemplateVersion < 0 ? 0 : requirementTemplateVersion;
    }

    public MobileIntakeCommand(String courierNo, String customerName, Long customerId,
            String processingRequirement, String dueDate, String goodsName,
            BigDecimal quantity, BigDecimal weight, String expressCompany,
            String materialCode, String quantityUnit, String requestId, String sourceType) {
        this(courierNo, customerName, customerId, processingRequirement, dueDate, goodsName,
                quantity, weight, expressCompany, materialCode, quantityUnit, requestId, sourceType, List.of(), "", 0);
    }

    public MobileIntakeCommand(String courierNo, String customerName, Long customerId,
            String processingRequirement, String dueDate, String goodsName,
            BigDecimal quantity, BigDecimal weight, String expressCompany,
            String materialCode, String quantityUnit, String requestId, String sourceType,
            List<MobileIntakeLine> lines) {
        this(courierNo, customerName, customerId, processingRequirement, dueDate, goodsName,
                quantity, weight, expressCompany, materialCode, quantityUnit, requestId, sourceType, lines, "", 0);
    }

    public MobileIntakeCommand(String courierNo, String customerName, Long customerId,
            String processingRequirement, String dueDate, String goodsName,
            BigDecimal quantity, BigDecimal weight, String expressCompany,
            String materialCode, String quantityUnit, String requestId) {
        this(courierNo, customerName, customerId, processingRequirement, dueDate, goodsName,
                quantity, weight, expressCompany, materialCode, quantityUnit, requestId, "COURIER", List.of(), "", 0);
    }

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
                quantity, weight, expressCompany, materialCode, quantityUnit, null, "COURIER", List.of(), "", 0);
    }
}
