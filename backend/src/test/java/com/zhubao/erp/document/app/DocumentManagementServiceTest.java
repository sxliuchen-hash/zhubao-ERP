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
}
