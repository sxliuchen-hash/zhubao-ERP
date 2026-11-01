package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.WholeBagReport;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/**
 * 整袋报工成功结果。回放只返回这段文字，不重新执行数量和照片。
 */
final class WholeBagReportText {

    private WholeBagReportText() {
    }

    static String write(WholeBagReport report) {
        return String.join("|",
                report.batchCode(),
                report.requestId(),
                report.bagId(),
                report.stepId(),
                report.quantity().toPlainString(),
                report.attachmentCode(),
                report.progressPercent() == null ? "" : report.progressPercent().toString(),
                report.createdAt(),
                Integer.toString(report.bagVersion()),
                report.targetCode(),
                report.operatorName(), report.workerAccount() == null ? "" : report.workerAccount());
    }

    static WholeBagReport read(String text) {
        String[] parts = text == null ? new String[0] : text.split("\\|", -1);
        if (parts.length != 11 && parts.length != 12) {
            throw new BusinessException("整袋报工结果无法识别");
        }
        Integer progress = parts[6].isEmpty() ? null : Integer.valueOf(parts[6]);
        return new WholeBagReport(
                parts[0], parts[1], parts[2], Integer.parseInt(parts[8]), parts[9], parts[3],
                new BigDecimal(parts[4]), parts[5], parts[10], progress, parts[7],
                parts.length == 12 && !parts[11].isBlank() ? parts[11] : null);
    }
}
