package com.zhubao.erp.document.domain;

public record DocumentCaptureView(
        String code,
        String businessCode,
        String scene,
        String fileName,
        String mediaType,
        String checksum,
        String uploadedAt,
        String owner,
        String previewUrl
) {
}
