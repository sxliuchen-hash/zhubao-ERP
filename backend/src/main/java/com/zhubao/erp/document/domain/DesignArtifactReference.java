package com.zhubao.erp.document.domain;

import com.zhubao.erp.common.error.BusinessException;

/**
 * 模型文件的外部引用。只保存说明和轻量凭据编号，不读取网址或本机路径。
 */
public record DesignArtifactReference(
        String fileName,
        String format,
        String version,
        String locationNote,
        String evidenceCode
) {
    public DesignArtifactReference {
        fileName = required(fileName, "缺少模型文件名");
        format = required(format, "缺少模型格式");
        version = required(version, "缺少模型版本");
        locationNote = locationNote == null ? "" : locationNote.trim();
        evidenceCode = evidenceCode == null ? "" : evidenceCode.trim();
        if (looksFetchable(locationNote)) {
            throw new BusinessException("模型存放说明不能是网址或本机路径");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private static boolean looksFetchable(String note) {
        String text = note.toLowerCase();
        return text.startsWith("http://") || text.startsWith("https://") || text.startsWith("file:")
                || text.matches("^[a-zA-Z]:\\\\.*") || text.startsWith("\\\\");
    }
}
