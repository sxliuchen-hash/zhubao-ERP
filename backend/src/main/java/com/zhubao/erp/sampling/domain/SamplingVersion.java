package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DesignArtifactReference;

/**
 * 打样的一个图稿版本。正文只在建立时写入。
 * 模型只保留外部引用，不表示原件已经备份到服务器。
 */
public record SamplingVersion(
        String samplingCode,
        SamplingVersionKind kind,
        int versionNo,
        String parameterText,
        String evidenceCode,
        String modelFileName,
        String modelFormat,
        String modelVersion,
        String modelLocationNote,
        String modelEvidenceCode,
        String openedBy,
        String openedOn
) {
    public SamplingVersion {
        BusinessTargetReference reference = BusinessTargetReference.fromCode(samplingCode);
        if (reference.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("版本必须挂在打样单上");
        }
        samplingCode = reference.code();
        if (kind == null) {
            throw new BusinessException("请选择平设、模型或珠型");
        }
        if (versionNo < 1) {
            throw new BusinessException("版本号无效");
        }
        parameterText = text(parameterText);
        if (parameterText.length() > 500) {
            throw new BusinessException("尺寸参数超过可保存长度");
        }
        evidenceCode = referenceCode(evidenceCode, "截图凭据");
        modelFileName = text(modelFileName);
        modelFormat = text(modelFormat);
        modelVersion = text(modelVersion);
        modelLocationNote = text(modelLocationNote);
        modelEvidenceCode = referenceCode(modelEvidenceCode, "模型凭据");
        boolean anyModel = !modelFileName.isEmpty() || !modelFormat.isEmpty() || !modelVersion.isEmpty()
                || !modelLocationNote.isEmpty() || !modelEvidenceCode.isEmpty();
        if (anyModel && kind != SamplingVersionKind.MODEL) {
            throw new BusinessException("只有模型版本可以登记模型引用");
        }
        if (modelFileName.length() > 120 || modelFormat.length() > 24 || modelVersion.length() > 40
                || modelLocationNote.length() > 200) {
            throw new BusinessException("模型引用超过可保存长度");
        }
        if (anyModel) {
            if (modelFileName.isEmpty() || modelFormat.isEmpty() || modelVersion.isEmpty()) {
                throw new BusinessException("模型引用不完整");
            }
            DesignArtifactReference artifact = new DesignArtifactReference(
                    modelFileName, modelFormat, modelVersion, modelLocationNote, modelEvidenceCode);
            modelFileName = artifact.fileName();
            modelFormat = artifact.format();
            modelVersion = artifact.version();
            modelLocationNote = artifact.locationNote();
            modelEvidenceCode = artifact.evidenceCode();
        }
        if (parameterText.isEmpty() && !anyModel) {
            throw new BusinessException(kind == SamplingVersionKind.MODEL
                    ? "请填写尺寸参数或模型引用" : "请填写尺寸参数");
        }
        openedBy = limited(openedBy, "操作人不能为空", "操作人超过可保存长度", 64);
        openedOn = text(openedOn);
        if (!openedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("版本日期无效");
        }
    }

    public boolean modelReferenced() {
        return !modelFileName.isEmpty();
    }

    public String identity() {
        return samplingCode + "|" + kind.name() + "|" + versionNo;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limited(String value, String empty, String tooLong, int max) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(empty);
        }
        if (text.length() > max) {
            throw new BusinessException(tooLong);
        }
        return text;
    }

    static String referenceCode(String value, String label) {
        String text = text(value);
        if (text.isEmpty()) {
            return "";
        }
        if (text.length() > 80) {
            throw new BusinessException(label + "超过可保存长度");
        }
        String lower = text.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")
                || lower.matches("^[a-zA-Z]:\\\\.*") || lower.startsWith("\\\\")) {
            throw new BusinessException(label + "只能登记编号，不能填写网址或路径");
        }
        return text;
    }
}
