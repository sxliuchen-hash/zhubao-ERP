package com.zhubao.erp.business.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 来料和入库的规范化内容与可回放结果。 */
final class ReceivingResultText {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ReceivingResultText() {
    }

    static String intakePayload(MobileIntakeCommand command, long customerId, String customerName) {
        return join(
                "customerId=" + customerId,
                "customerName=" + (command.customerId() != null && command.customerId() > 0 ? "" : text(customerName)),
                "sourceType=" + text(command.sourceType()),
                "courierNo=" + text(command.courierNo()),
                "requirement=" + text(command.processingRequirement()),
                "dueDate=" + text(command.dueDate()),
                "goodsName=" + text(command.goodsName()),
                "quantity=" + number(command.quantity()),
                "weight=" + number(command.weight()),
                "express=" + text(command.expressCompany()),
                "material=" + text(blankTo(command.materialCode(), "MAT-MOBILE")),
                "unit=" + text(blankTo(command.quantityUnit(), "件")),
                "template=" + text(command.requirementTemplateCode()),
                "templateVersion=" + command.requirementTemplateVersion(),
                "lines=" + command.lines().stream().map(line -> text(line.materialCode()) + "|"
                        + text(line.goodsName()) + "|" + number(line.quantity()) + "|"
                        + text(blankTo(line.quantityUnit(), "件")) + "|" + number(line.weight())).toList()
        ) + (command.pendingCode().isBlank() ? "" : ";pendingCode=" + command.pendingCode() + ";pendingVersion=" + command.pendingVersion());
    }

    static String inboundPayload(ConfirmInboundCommand command) {
        return join(
                "customerId=" + command.customerId(),
                "ownerCustomerId=" + (command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId()),
                "customerName=" + text(command.customerName()),
                "courierNo=" + text(command.courierNo()),
                "requirement=" + text(command.processingRequirement()),
                "dueDate=" + text(command.dueDate()),
                "goodsName=" + text(command.goodsName()),
                "quantity=" + number(command.quantity()),
                "weight=" + number(command.weight()),
                "warehouse=" + text(command.warehouseCode()),
                "material=" + text(command.materialCode()),
                "unit=" + text(blankTo(command.quantityUnit(), "件")),
                "express=" + text(command.expressCompany())
        );
    }

    static String intake(MobileIntakeResult result) {
        return write("intake", result);
    }

    static MobileIntakeResult intake(String text) {
        return read(text, MobileIntakeResult.class);
    }

    static String inbound(InboundConfirmationResult result) {
        return write("inbound", result);
    }

    static InboundConfirmationResult inbound(String text) {
        return read(text, InboundConfirmationResult.class);
    }

    private static String join(String... parts) {
        return String.join(";", parts);
    }

    private static String write(String kind, Object value) {
        try {
            return kind + ":" + JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法保存收货结果", ex);
        }
    }

    private static <T> T read(String text, Class<T> type) {
        int split = text.indexOf(':');
        if (split < 0) {
            throw new BusinessException("收货结果无法识别");
        }
        try {
            return JSON.readValue(text.substring(split + 1), type);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("收货结果无法识别");
        }
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankTo(String value, String fallback) {
        String text = text(value);
        return text.isEmpty() ? fallback : text;
    }
}
