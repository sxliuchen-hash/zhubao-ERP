package com.zhubao.erp.business.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 通用报工的规范化内容与可回放结果。 */
final class ProductionReportResultText {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProductionReportResultText() {
    }

    static String payload(ReportProductionCommand command) {
        Long owner = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        return String.join(";",
                "mother=" + text(command.motherOrderCode()),
                "work=" + text(command.workOrderCode()),
                "customer=" + command.customerId(),
                "owner=" + owner,
                "material=" + text(command.materialCode()),
                "from=" + text(command.fromWarehouseCode()),
                "to=" + text(command.toWarehouseCode()),
                "quantity=" + number(command.quantity()),
                "weight=" + number(command.weight()),
                "unit=" + text(command.quantityUnit()),
                "process=" + text(command.process()),
                "completion=" + text(command.completionStatus()),
                "audit=" + text(command.handoffAuditStatus())
        );
    }

    static String write(ProductionReportResult result) {
        try {
            return "production:" + JSON.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法保存报工结果", ex);
        }
    }

    static ProductionReportResult read(String text) {
        if (text == null || !text.startsWith("production:")) {
            throw new BusinessException("报工结果无法识别");
        }
        try {
            return JSON.readValue(text.substring("production:".length()), ProductionReportResult.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("报工结果无法识别");
        }
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
