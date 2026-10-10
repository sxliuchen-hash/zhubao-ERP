package com.zhubao.erp.scan.domain;

/** 已经交付的工序，不表示本次又完成了一次。 */
public record ScanHandoffFact(
        String process,
        String operator,
        String ledgerCode,
        String createdAt
) {
}
