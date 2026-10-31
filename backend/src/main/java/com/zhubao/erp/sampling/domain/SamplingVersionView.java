package com.zhubao.erp.sampling.domain;

/**
 * 打样版本及客户决定。模型引用不表示外部原件已备份。没有数量和金额。
 */
public record SamplingVersionView(
        String samplingCode,
        String kind,
        String kindLabel,
        int versionNo,
        String parameterText,
        String evidenceCode,
        String modelFileName,
        String modelFormat,
        String modelVersion,
        String modelLocationNote,
        String modelEvidenceCode,
        boolean modelReferenced,
        String sourceCustody,
        String backupClaim,
        String decision,
        String decisionLabel,
        String decisionReason,
        String decisionCredential,
        String decidedBy,
        String decidedOn
) {
}
