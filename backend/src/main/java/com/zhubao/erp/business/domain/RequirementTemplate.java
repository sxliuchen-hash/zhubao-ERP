package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 全厂共用的加工要求组合。步骤是文字快照，不代表正式报工工序。
 */
public record RequirementTemplate(
        long templateId,
        String templateCode,
        String name,
        List<String> steps,
        boolean enabled,
        int sortOrder,
        int version,
        String requestId
) {
    public RequirementTemplate {
        steps = steps == null ? List.of() : List.copyOf(steps);
        requestId = requestId == null ? "" : requestId;
    }
}
