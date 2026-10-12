package com.zhubao.erp.business.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.OutboundShipmentResult;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 确认发货的规范化内容与可回放结果。 */
final class ShipmentResultText {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ShipmentResultText() {
    }

    static String payload(ShipFinishedStockCommand command) {
        Long owner = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        return String.join(";",
                "customerId=" + command.customerId(),
                "ownerCustomerId=" + owner,
                "motherOrderCode=" + text(command.motherOrderCode()),
                "workOrderCode=" + text(command.workOrderCode()),
                "materialCode=" + text(command.materialCode()),
                "fromWarehouseCode=" + text(command.fromWarehouseCode()),
                "toWarehouseCode=" + text(command.toWarehouseCode()),
                "quantity=" + number(command.quantity()),
                "weight=" + number(command.weight()),
                "expressCompany=" + text(command.expressCompany()),
                "logisticsNo=" + text(command.logisticsNo())
        );
    }

    static String write(OutboundShipmentResult result) {
        try {
            return "shipment:" + JSON.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法保存发货结果", ex);
        }
    }

    static OutboundShipmentResult read(String text) {
        if (text == null || !text.startsWith("shipment:")) {
            throw new BusinessException("发货结果无法识别");
        }
        try {
            return JSON.readValue(text.substring("shipment:".length()), OutboundShipmentResult.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("发货结果无法识别");
        }
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
