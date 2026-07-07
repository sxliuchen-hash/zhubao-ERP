package com.zhubao.erp.security.domain;

import java.util.List;

public record SecurityCurrentContext(
        String operator,
        String role,
        String roleLabel,
        String dataScope,
        String dataScopeLabel,
        String dataBoundary,
        boolean headerBased,
        List<PermissionGrant> allowedPermissions,
        List<String> sensitiveActionCodes,
        List<String> limitationNotes,
        HeaderContract headerContract
) {

    public record PermissionGrant(
            String code,
            String label
    ) {
    }

    public record HeaderContract(
            String operatorHeader,
            String roleHeader,
            String dataScopeHeader
    ) {
    }
}
