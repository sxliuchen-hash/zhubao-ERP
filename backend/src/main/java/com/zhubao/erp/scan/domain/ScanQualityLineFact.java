package com.zhubao.erp.scan.domain;

/** 已经保存的质检条款。未提交的空白判定不放进正式事实。 */
public record ScanQualityLineFact(
        int lineNo,
        String clause,
        String verdict,
        String reason
) {
}
