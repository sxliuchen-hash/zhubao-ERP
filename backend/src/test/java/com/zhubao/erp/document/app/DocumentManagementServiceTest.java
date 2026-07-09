package com.zhubao.erp.document.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.document.infra.LocalAttachmentObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentManagementServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesDocumentPackagesAttachmentsAndAuditSummary() {
        DocumentManagementService service = newService();

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
        InMemoryDocumentManagementRepository repository = new InMemoryDocumentManagementRepository();
        DocumentManagementService service = new DocumentManagementService(repository, localStorage());
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
        assertThat(uploaded.storageLocation()).contains("local://evidence/DOC-INBOUND-20260706-001");
        assertThat(uploaded.checksum()).startsWith("sha256:");
        assertThat(uploaded.previewUrl()).contains(uploaded.code());
        assertThat(uploaded.previewUrl())
                .contains("/content?expiresAt=")
                .contains("&token=")
                .doesNotContain("preview-demo");
        var previewUrl = service.previewUrl(uploaded.code());
        assertThat(previewUrl.url())
                .contains("/content?expiresAt=")
                .contains("&token=")
                .doesNotContain("preview-demo");
        long expiresAt = expiresAt(previewUrl.url());
        String token = token(previewUrl.url());
        assertThat(service.previewContent(uploaded.code(), expiresAt, token).content()).isEqualTo(content);
        assertThatThrownBy(() -> service.previewContent(uploaded.code(), expiresAt, "invalid-token"))
                .isInstanceOf(BusinessException.class);
        DocumentManagementService expiredPreviewService = new DocumentManagementService(
                repository,
                localStorage(),
                new DocumentPreviewTokenProperties(),
                Clock.fixed(Instant.ofEpochSecond(expiresAt + 1), ZoneId.systemDefault())
        );
        assertThatThrownBy(() -> expiredPreviewService.previewContent(uploaded.code(), expiresAt, token))
                .isInstanceOf(BusinessException.class);
        DocumentManagementService reloadedService = new DocumentManagementService(repository, localStorage());
        assertThat(reloadedService.content(uploaded.code()).content()).isEqualTo(content);
        assertThat(Files.exists(tempDir.resolve(uploaded.storageLocation().replace("local://", "")))).isTrue();
        assertThat(service.summary().attachments()).isEqualTo(6);
        assertThat(service.auditEvents()).extracting("action").contains("上传附件");
    }

    @Test
    void savesDynamicDocumentPackageForPreApprovalEvidence() {
        DocumentManagementService service = newService();

        service.savePackage(new DocumentPackage(
                "DOC-AUTH-PRE-APP-001",
                "管理员重置密码证据包",
                "账号前置审批",
                "AUTH-PRE-APP-001",
                "系统管理员",
                "归档账号前置审批证据。",
                1,
                0,
                "待补本人核验附件",
                "账号审批证据长期保留",
                "2026-07-09 12:00:00",
                "amber"
        ));
        service.savePackage(new DocumentPackage(
                "DOC-AUTH-PRE-APP-001",
                "管理员重置密码证据包",
                "账号前置审批",
                "AUTH-PRE-APP-001",
                "系统管理员",
                "归档账号前置审批证据。",
                1,
                1,
                "已关联证据",
                "账号审批证据长期保留",
                "2026-07-09 12:05:00",
                "green"
        ));

        assertThat(service.packages())
                .filteredOn(documentPackage -> documentPackage.code().equals("DOC-AUTH-PRE-APP-001"))
                .singleElement()
                .satisfies(documentPackage -> {
                    assertThat(documentPackage.linkedEvidence()).isEqualTo(1);
                    assertThat(documentPackage.status()).isEqualTo("已关联证据");
                    assertThat(documentPackage.tone()).isEqualTo("green");
                });
    }

    private DocumentManagementService newService() {
        return new DocumentManagementService(new InMemoryDocumentManagementRepository(), localStorage());
    }

    private LocalAttachmentObjectStorage localStorage() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        return new LocalAttachmentObjectStorage(properties);
    }

    private long expiresAt(String previewUrl) {
        return Long.parseLong(previewUrl.replaceAll(".*[?&]expiresAt=([0-9]+).*", "$1"));
    }

    private String token(String previewUrl) {
        return previewUrl.replaceAll(".*[?&]token=([^&]+).*", "$1");
    }
}
