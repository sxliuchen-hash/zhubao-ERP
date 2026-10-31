package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

/** 对已有版本追加的一次确认或驳回。不能改版本正文。 */
public record SamplingVersionDecision(
        String samplingCode,
        SamplingVersionKind kind,
        int versionNo,
        SamplingVersionDecisionKind decision,
        String reason,
        String credentialCode,
        String decidedBy,
        String decidedOn
) {
    public SamplingVersionDecision {
        BusinessTargetReference reference = BusinessTargetReference.fromCode(samplingCode);
        if (reference.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("确认记录必须挂在打样单上");
        }
        samplingCode = reference.code();
        if (kind == null || decision == null) {
            throw new BusinessException("确认记录不完整");
        }
        if (versionNo < 1) {
            throw new BusinessException("版本号无效");
        }
        reason = reason == null ? "" : reason.trim();
        if (reason.length() > 200) {
            throw new BusinessException("确认说明超过可保存长度");
        }
        if (decision == SamplingVersionDecisionKind.REJECTED && reason.isEmpty()) {
            throw new BusinessException("请填写驳回原因");
        }
        credentialCode = SamplingVersion.referenceCode(credentialCode, "确认凭据");
        decidedBy = decidedBy == null ? "" : decidedBy.trim();
        if (decidedBy.isEmpty() || decidedBy.length() > 64) {
            throw new BusinessException(decidedBy.isEmpty() ? "操作人不能为空" : "操作人超过可保存长度");
        }
        decidedOn = decidedOn == null ? "" : decidedOn.trim();
        if (!decidedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("确认日期无效");
        }
    }
}
