package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 某张单据保存下来的实际路线。已确认版本不随模板改写。
 */
public record RouteSnapshot(
        String snapshotId,
        BusinessKind kind,
        String targetCode,
        int version,
        List<String> stepIds,
        boolean confirmed
) {
    public RouteSnapshot {
        stepIds = stepIds == null ? List.of() : List.copyOf(stepIds);
    }
}
