package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.OrderRequirementVersion;
import com.zhubao.erp.business.domain.WorkshopProcesses;

import java.util.List;
import java.util.Optional;

/**
 * 岗位和质检读取当前有效要求。没有生效版本时继续使用原单上的文字。
 */
public final class OrderRequirementLookup {

    private OrderRequirementLookup() {
    }

    public static Optional<OrderRequirementVersion> current(OrderRequirementRepository repository, String motherOrderCode, String workOrderCode) {
        if (repository == null || motherOrderCode == null || motherOrderCode.isBlank()) {
            return Optional.empty();
        }
        String key = workOrderCode == null || workOrderCode.isBlank() || workOrderCode.equals(motherOrderCode) ? "" : workOrderCode;
        Optional<OrderRequirementVersion> exact = repository.current(motherOrderCode, key);
        if (exact.isPresent() || key.isEmpty()) {
            return exact;
        }
        return repository.current(motherOrderCode, "");
    }

    public static String requirement(OrderRequirementVersion version, String original) {
        if (version == null || version.requirementText() == null || version.requirementText().isBlank()) {
            return original == null ? "" : original;
        }
        return version.requirementText();
    }

    public static String dueDate(OrderRequirementVersion version, String original) {
        if (version == null || version.dueDate() == null || version.dueDate().isBlank()) {
            return original == null ? "" : original;
        }
        return version.dueDate();
    }

    public static List<String> route(OrderRequirementVersion version, List<String> original) {
        if (version == null || version.routeText() == null || version.routeText().isBlank()) {
            return original;
        }
        List<String> stored = WorkshopProcesses.split(version.routeText());
        return stored.isEmpty() ? original : WorkshopProcesses.normalize(stored);
    }
}