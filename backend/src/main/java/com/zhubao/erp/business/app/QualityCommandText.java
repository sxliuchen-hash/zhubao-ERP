package com.zhubao.erp.business.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityCheckResult;
import com.zhubao.erp.business.domain.QualityReleaseResult;
import com.zhubao.erp.business.domain.QualityReworkResult;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.common.error.BusinessException;

import java.util.List;

/** 质检命令的规范化内容与可回放结果。 */
final class QualityCommandText {

    private static final ObjectMapper JSON = new ObjectMapper();

    private QualityCommandText() {
    }

    static String payload(long customerId, long ownerCustomerId, String motherOrderCode, List<QualityCheckLine> lines) {
        return String.join(";",
                "customerId=" + customerId,
                "ownerCustomerId=" + ownerCustomerId,
                "motherOrderCode=" + text(motherOrderCode),
                "lines=" + linesText(lines)
        );
    }

    static String payload(long customerId, long ownerCustomerId, String motherOrderCode, String marker) {
        return String.join(";",
                "customerId=" + customerId,
                "ownerCustomerId=" + ownerCustomerId,
                "motherOrderCode=" + text(motherOrderCode),
                "marker=" + text(marker)
        );
    }

    static String writeCheck(QualityCheckResult result) {
        return write("check", result);
    }

    static QualityCheckResult readCheck(String text) {
        return read("check", text, QualityCheckResult.class);
    }

    static String writeRework(QualityReworkResult result) {
        return write("rework", result);
    }

    static QualityReworkResult readRework(String text) {
        return read("rework", text, QualityReworkResult.class);
    }

    static String writeReport(ReworkReportResult result) {
        return write("report", result);
    }

    static ReworkReportResult readReport(String text) {
        return read("report", text, ReworkReportResult.class);
    }

    static String writeRelease(QualityReleaseResult result) {
        return write("release", result);
    }

    static QualityReleaseResult readRelease(String text) {
        return read("release", text, QualityReleaseResult.class);
    }

    private static String linesText(List<QualityCheckLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (QualityCheckLine line : lines) {
            if (!builder.isEmpty()) {
                builder.append('|');
            }
            builder.append(line.lineNo())
                    .append('=')
                    .append(text(line.verdict()))
                    .append(':')
                    .append(text(line.reason()));
        }
        return builder.toString();
    }

    private static String write(String prefix, Object result) {
        try {
            return prefix + ":" + JSON.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法保存质检结果", ex);
        }
    }

    private static <T> T read(String prefix, String text, Class<T> type) {
        String head = prefix + ":";
        if (text == null || !text.startsWith(head)) {
            throw new BusinessException("质检结果无法识别");
        }
        try {
            return JSON.readValue(text.substring(head.length()), type);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("质检结果无法识别");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
