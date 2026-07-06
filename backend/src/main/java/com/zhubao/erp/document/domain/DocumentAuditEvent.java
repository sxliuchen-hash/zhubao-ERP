package com.zhubao.erp.document.domain;

public record DocumentAuditEvent(
        String time,
        String eventCode,
        String documentCode,
        String action,
        String operator,
        String detail,
        String tone
) {
}
