package com.zhubao.erp.business.domain;

/**
 * 某次改单生效后的要求、期限和路线。原母单和子单行保持不变。
 */
public record OrderRequirementVersion(
        String id,
        String motherOrderCode,
        String workOrderCode,
        int versionNo,
        String requirementText,
        String dueDate,
        String routeText,
        String changeId,
        String effectiveAt
) {
}