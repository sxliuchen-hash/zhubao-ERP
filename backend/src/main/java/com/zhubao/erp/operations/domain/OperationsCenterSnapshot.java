package com.zhubao.erp.operations.domain;

import java.util.List;

public record OperationsCenterSnapshot(
        String generatedAt,
        Summary summary,
        List<ServiceStatus> services,
        List<RuntimeMetric> runtimeMetrics,
        List<ErrorSummary> errorSummaries,
        List<RecentLogEntry> recentLogs,
        List<BackupStatus> backupStatuses,
        List<OperationChecklistItem> checklist
) {
    public OperationsCenterSnapshot {
        services = List.copyOf(services);
        runtimeMetrics = List.copyOf(runtimeMetrics);
        errorSummaries = List.copyOf(errorSummaries);
        recentLogs = List.copyOf(recentLogs);
        backupStatuses = List.copyOf(backupStatuses);
        checklist = List.copyOf(checklist);
    }

    public record Summary(
            String overallStatus,
            int serviceCount,
            int degradedServices,
            int openErrors,
            int criticalErrors,
            String backupStatus,
            long uptimeMinutes
    ) {
    }

    public record ServiceStatus(
            String code,
            String name,
            String status,
            String endpoint,
            String detail,
            String owner,
            String checkedAt,
            String tone
    ) {
    }

    public record RuntimeMetric(
            String code,
            String label,
            String value,
            String unit,
            String detail,
            String tone
    ) {
    }

    public record ErrorSummary(
            String code,
            String module,
            String severity,
            int count,
            String latest,
            String owner,
            String nextAction,
            String tone
    ) {
    }

    public record RecentLogEntry(
            String time,
            String level,
            String source,
            String message,
            String traceId,
            String tone
    ) {
    }

    public record BackupStatus(
            String code,
            String target,
            String status,
            String lastSuccessAt,
            String nextDueAt,
            String storage,
            String detail,
            String tone
    ) {
    }

    public record OperationChecklistItem(
            String code,
            String item,
            String status,
            String evidence,
            String owner,
            String tone
    ) {
    }
}
