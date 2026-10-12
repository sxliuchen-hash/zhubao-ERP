package com.zhubao.erp.business.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.ProcessStationReportResult;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 工序交付的规范化内容与可回放结果。 */
final class ProcessHandoffResultText {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProcessHandoffResultText() {
    }

    static String payload(
            long customerId,
            long ownerCustomerId,
            String motherOrderCode,
            String process,
            BigDecimal quantity,
            BigDecimal weight
    ) {
        return String.join(";",
                "customerId=" + customerId,
                "ownerCustomerId=" + ownerCustomerId,
                "motherOrderCode=" + text(motherOrderCode),
                "process=" + text(process),
                "quantity=" + number(quantity),
                "weight=" + number(weight)
        );
    }

    static String write(ProcessStationReportResult result) {
        try {
            return "station:" + JSON.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法保存工序交付结果", ex);
        }
    }

    static ProcessStationReportResult read(String text) {
        int split = text.indexOf(':');
        if (split < 0 || !text.startsWith("station:")) {
            throw new BusinessException("工序交付结果无法识别");
        }
        try {
            return JSON.readValue(text.substring(split + 1), ProcessStationReportResult.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("工序交付结果无法识别");
        }
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
