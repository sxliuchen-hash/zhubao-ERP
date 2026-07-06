package com.zhubao.erp.document.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentManagementServiceTest {

    @Test
    void exposesDocumentPackagesAttachmentsAndAuditSummary() {
        DocumentManagementService service = new DocumentManagementService();

        assertThat(service.packages()).hasSize(4);
        assertThat(service.attachments())
                .anySatisfy(attachment -> {
                    assertThat(attachment.code()).isEqualTo("ATT-QC-20260706-021");
                    assertThat(attachment.documentCode()).isEqualTo("DOC-QC-20260706-021");
                    assertThat(attachment.status()).isEqualTo("已归档");
                });
        assertThat(service.summary().attachments()).isEqualTo(5);
        assertThat(service.summary().missingEvidence()).isEqualTo(3);
        assertThat(service.summary().manualFallbacks()).isEqualTo(3);
        assertThat(service.auditEvents()).extracting("eventCode").contains("DOC-AUD-004");
    }

    @Test
    void uploadsAttachmentAndExposesPreviewContent() {
        DocumentManagementService service = new DocumentManagementService();
        byte[] content = "weight photo demo".getBytes();

        var uploaded = service.uploadAttachment(
                "DOC-INBOUND-20260706-001",
                "称重交接",
                "HO-20260706-014",
                "weight-photo.jpg",
                "image/jpeg",
                "PC 后台上传",
                "库管员 何琳",
                "无",
                content
        );

        assertThat(uploaded.code()).startsWith("ATT-UP-");
        assertThat(uploaded.storageLocation()).contains("memory://evidence/DOC-INBOUND-20260706-001");
        assertThat(uploaded.checksum()).startsWith("sha256:");
        assertThat(uploaded.previewUrl()).contains(uploaded.code());
        assertThat(service.previewUrl(uploaded.code()).url()).contains("/content?token=preview-demo");
        assertThat(service.content(uploaded.code()).content()).isEqualTo(content);
        assertThat(service.summary().attachments()).isEqualTo(6);
        assertThat(service.auditEvents()).extracting("action").contains("上传附件");
    }
}
