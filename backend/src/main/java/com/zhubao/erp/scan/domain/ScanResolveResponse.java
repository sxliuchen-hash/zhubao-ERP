package com.zhubao.erp.scan.domain;

public record ScanResolveResponse(
        ScanObjectType objectType,
        String code,
        String nextAction,
        boolean allowed
) {
}
