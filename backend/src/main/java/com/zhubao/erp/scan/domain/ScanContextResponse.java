package com.zhubao.erp.scan.domain;

import java.util.List;

/**
 * 统一扫码上下文。查询结果不是后续写操作的授权凭据。
 */
public record ScanContextResponse(
        ScanContextTarget target,
        ScanContextOrder order,
        ScanContextRequirements requirements,
        ScanContextProgress progress,
        List<ScanRelatedTarget> relatedTargets,
        boolean requiresSelection,
        List<ScanContextAction> actions,
        ScanOperationContext operationContext
) {
    public ScanContextResponse {
        relatedTargets = relatedTargets == null ? List.of() : List.copyOf(relatedTargets);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
