package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

/** 普通加工引用的样品版本快照。后来的样品改版不改这张快照。 */
public record SamplingProductionLink(
        String motherCode,
        String samplingCode,
        SamplingVersionKind versionKind,
        int versionNo,
        String snapshotText,
        String linkedOn
) {
    public SamplingProductionLink {
        BusinessTargetReference mother = BusinessTargetReference.fromCode(motherCode);
        if (!mother.processingMother()) {
            throw new BusinessException("只能引用到普通加工母单");
        }
        motherCode = mother.code();
        BusinessTargetReference sampling = BusinessTargetReference.fromCode(samplingCode);
        if (sampling.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("只能引用打样单");
        }
        samplingCode = sampling.code();
        if (versionKind == null || versionNo < 1) {
            throw new BusinessException("请选择已确认版本");
        }
        snapshotText = snapshotText == null ? "" : snapshotText.trim();
        if (snapshotText.isEmpty()) {
            throw new BusinessException("样品版本没有可引用的内容");
        }
        linkedOn = linkedOn == null ? "" : linkedOn.trim();
        if (!linkedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("关联日期无效");
        }
    }
}
