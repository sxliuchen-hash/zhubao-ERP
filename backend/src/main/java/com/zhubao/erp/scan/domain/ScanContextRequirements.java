package com.zhubao.erp.scan.domain;

import java.util.List;

/**
 * 母单要求、子单快照和当前账号可见的岗位要求分开保留。
 */
public record ScanContextRequirements(
        String motherRequirement,
        String motherSource,
        String workOrderRequirement,
        String workOrderSource,
        List<ScanRequirementItem> stationRequirements
) {
    public ScanContextRequirements {
        stationRequirements = stationRequirements == null ? List.of() : List.copyOf(stationRequirements);
    }
}
