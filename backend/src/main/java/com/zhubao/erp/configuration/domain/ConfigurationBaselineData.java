package com.zhubao.erp.configuration.domain;

import java.util.List;

public final class ConfigurationBaselineData {

    private ConfigurationBaselineData() {
    }

    public record ConfigurationBaselineSummary(
            int systemParameters,
            int codeRules,
            int roleBaselines,
            int processTemplates,
            int warehouseStates,
            int videoParameters,
            int approvalRequiredItems
    ) {
    }

    public record SystemParameter(
            String key,
            String value,
            String category,
            String level,
            String description,
            String changeControl,
            String effectiveScope
    ) {
    }

    public record CodeRule(
            String objectType,
            String format,
            String example,
            String uniqueness,
            String changePolicy
    ) {
    }

    public record RolePermissionBaseline(
            String role,
            String dataScope,
            List<String> permissions,
            List<String> sensitiveActions
    ) {
    }

    public record ProcessTemplate(
            String code,
            String name,
            String materialScope,
            List<String> steps,
            String lossRule,
            String wageRule,
            String qualityRule,
            String snapshotFields
    ) {
    }

    public record WarehouseState(
            String code,
            String name,
            String ownerBoundary,
            String stockStatus,
            String transitionRule,
            boolean negativeAllowed
    ) {
    }

    public record VideoParameter(
            String key,
            String value,
            String scene,
            String fallback,
            String retention,
            String changeControl
    ) {
    }

    public record ComponentBaseline(
            String component,
            String version,
            String license,
            String purpose,
            String source,
            String risk
    ) {
    }

    public record BackupPolicy(
            String target,
            String strategy,
            String rpo,
            String rto,
            String owner
    ) {
    }
}
