package com.zhubao.erp.document.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.StoredContentInventory;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static com.zhubao.erp.document.AttachmentTestMedia.jpeg;

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
        byte[] content = jpeg("weight photo demo");

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
                .contains("&purpose=preview&resolution=original")
                .contains("&token=")
                .doesNotContain("preview-demo");
        var previewUrl = service.previewUrl(uploaded.code());
        assertThat(previewUrl.url())
                .contains("/content?expiresAt=")
                .contains("&purpose=preview&resolution=original")
                .contains("&token=")
                .doesNotContain("preview-demo");
        long expiresAt = expiresAt(previewUrl.url());
        String token = token(previewUrl.url());
        assertThat(service.previewContent(uploaded.code(), expiresAt, token).content()).isEqualTo(content);
        var downloadUrl = service.previewUrl(uploaded.code(), "download");
        assertThat(downloadUrl.url()).contains("&purpose=download&resolution=original");
        assertThat(service.previewContent(uploaded.code(), expiresAt(downloadUrl.url()), token(downloadUrl.url()),
                "download", "original").content()).isEqualTo(content);
        assertThatThrownBy(() -> service.previewContent(uploaded.code(), expiresAt, token,
                "download", "original"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.previewContent(uploaded.code(), expiresAt, token,
                "preview", "thumbnail"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.previewUrl(uploaded.code(), "unknown"))
                .isInstanceOf(BusinessException.class);
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
    void inventoriesLocalFilesAndRetiresStoredContent() {
        InMemoryDocumentManagementRepository repository = new InMemoryDocumentManagementRepository();
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setProvider("local");
        properties.setLocalRoot(tempDir.toString());
        DocumentManagementService service = new DocumentManagementService(
                repository,
                new LocalAttachmentObjectStorage(properties),
                new DocumentPreviewTokenProperties(),
                Clock.systemDefaultZone(),
                properties
        );
        byte[] content = jpeg("weight photo demo");
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

        StoredContentInventory inventory = service.storageInventory();

        assertThat(inventory.provider()).isEqualTo("local");
        assertThat(inventory.usedBytes()).isEqualTo(content.length);
        assertThat(inventory.items())
                .filteredOn(item -> item.code().equals(uploaded.code()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sizeBytes()).isEqualTo(content.length);
                    assertThat(item.status()).isEqualTo("已上传");
                });

        var retired = service.retireStoredContent(uploaded.code(), "系统管理员");

        assertThat(retired.status()).isEqualTo("已下线");
        assertThat(retired.sizeBytes()).isEqualTo(content.length);
        assertThat(Files.exists(tempDir.resolve(uploaded.storageLocation().replace("local://", "")))).isTrue();
        assertThatThrownBy(() -> service.content(uploaded.code()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下线");
        DocumentManagementService reloaded = new DocumentManagementService(repository, new LocalAttachmentObjectStorage(properties));
        assertThatThrownBy(() -> reloaded.content(uploaded.code()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下线");
        assertThat(service.auditEvents()).extracting("action").contains("下线存储内容");
        assertThatThrownBy(() -> service.retireStoredContent(uploaded.code(), "系统管理员"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下线");
    }

    @Test
    void failedRetirementKeepsActiveMetadataAndOriginal() {
        InMemoryDocumentManagementRepository repository = spy(new InMemoryDocumentManagementRepository());
        DocumentManagementService service = new DocumentManagementService(repository, localStorage());
        var uploaded = service.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接", "HO-20260706-014",
                "weight-photo.jpg", "image/jpeg", "PC 后台上传", "库管员", "无", jpeg("original"));
        doThrow(new IllegalStateException("数据库中断"))
                .when(repository).saveAttachment(argThat(attachment -> "已下线".equals(attachment.status())));

        assertThatThrownBy(() -> service.retireStoredContent(uploaded.code(), "系统管理员"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.content(uploaded.code()).content()).isEqualTo(jpeg("original"));
        assertThat(Files.exists(tempDir.resolve(uploaded.storageLocation().replace("local://", "")))).isTrue();
    }

    @Test
    void restartInSameMinuteCannotReplaceEarlierAttachmentMetadata() {
        InMemoryDocumentManagementRepository repository = new InMemoryDocumentManagementRepository();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneId.of("UTC"));
        DocumentManagementService firstProcess = new DocumentManagementService(
                repository, localStorage(), new DocumentPreviewTokenProperties(), fixedClock);
        byte[] firstContent = jpeg("first-original");
        var first = firstProcess.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接", "HO-1",
                "first.jpg", "image/jpeg", "PC 后台上传", "库管员", "无", firstContent);

        DocumentManagementService restarted = new DocumentManagementService(
                repository, localStorage(), new DocumentPreviewTokenProperties(), fixedClock);
        var second = restarted.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接", "HO-1",
                "second.jpg", "image/jpeg", "PC 后台上传", "库管员", "无", jpeg("second-original"));

        assertThat(second.code()).isNotEqualTo(first.code());
        assertThat(repository.attachments()).filteredOn(row -> row.code().equals(first.code())).hasSize(1);
        assertThat(restarted.content(first.code()).content()).isEqualTo(firstContent);
        assertThat(Files.exists(tempDir.resolve(first.storageLocation().replace("local://", "")))).isTrue();
    }

    @Test
    void interruptedRetirementAuditReportsAppliedStateAndKeepsOriginal() {
        InMemoryDocumentManagementRepository repository = spy(new InMemoryDocumentManagementRepository());
        DocumentManagementService service = new DocumentManagementService(repository, localStorage());
        var uploaded = service.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接", "HO-20260706-014",
                "weight-photo.jpg", "image/jpeg", "PC 后台上传", "库管员", "无", jpeg("original"));
        doThrow(new IllegalStateException("审计库中断"))
                .when(repository).saveAuditEvent(argThat(event -> "下线存储内容".equals(event.action())));

        assertThatThrownBy(() -> service.retireStoredContent(uploaded.code(), "系统管理员"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下线且原件保留")
                .hasMessageContaining("审计记录结果未确认");
        assertThatThrownBy(() -> service.content(uploaded.code()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下线");
        assertThat(Files.exists(tempDir.resolve(uploaded.storageLocation().replace("local://", "")))).isTrue();
    }

    @Test
    void uncertainMetadataWriteKeepsPublishedOriginalForReconciliation() throws Exception {
        InMemoryDocumentManagementRepository repository = spy(new InMemoryDocumentManagementRepository());
        doThrow(new IllegalStateException("提交结果未知"))
                .when(repository).saveAttachment(argThat(attachment -> attachment.code().startsWith("ATT-UP-")));
        DocumentManagementService service = new DocumentManagementService(repository, localStorage());

        assertThatThrownBy(() -> service.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接",
                "HO-20260706-014", "weight-photo.jpg", "image/jpeg", "PC 后台上传", "库管员", "无",
                jpeg("original")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原件已保存")
                .hasMessageContaining("核对后处理");
        try (var paths = Files.walk(tempDir)) {
            assertThat(paths.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    @Test
    void reportsMissingTamperedUnregisteredAndPendingObjectsWithoutChangingFiles() throws Exception {
        InMemoryDocumentManagementRepository repository = new InMemoryDocumentManagementRepository();
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        DocumentManagementService service = new DocumentManagementService(repository,
                new LocalAttachmentObjectStorage(properties), new DocumentPreviewTokenProperties(),
                Clock.systemDefaultZone(), properties);
        var missing = service.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接", "HO-1",
                "missing.jpg", "image/jpeg", "PC 后台上传", "库管员", "无", jpeg("first"));
        var tampered = service.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接", "HO-1",
                "tampered.jpg", "image/jpeg", "PC 后台上传", "库管员", "无", jpeg("second"));
        Files.delete(tempDir.resolve(missing.storageLocation().replace("local://", "")));
        Files.writeString(tempDir.resolve(tampered.storageLocation().replace("local://", "")), "changed");
        Path orphan = tempDir.resolve("evidence/orphan.jpg");
        Files.writeString(orphan, "orphan");
        Path pending = tempDir.resolve("evidence/.attachment-interrupted.pending");
        Files.writeString(pending, "partial");

        var report = service.storageIntegrity();

        assertThat(report.issues()).extracting("category")
                .contains("MISSING_ORIGINAL", "CHECKSUM_MISMATCH", "UNREGISTERED_ORIGINAL", "PENDING_OBJECT");
        assertThat(Files.exists(orphan)).isTrue();
        assertThat(Files.exists(pending)).isTrue();
    }

    @Test
    void auditInterruptionLeavesPendingUntilAdministratorVerifiesOriginalAfterRestart() {
        InMemoryDocumentManagementRepository repository = spy(new InMemoryDocumentManagementRepository());
        doThrow(new IllegalStateException("审计库中断"))
                .when(repository).saveAuditEvent(argThat(event -> event.eventCode().startsWith("DOC-AUD-ATT-UP-")));
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(properties);
        DocumentManagementService service = new DocumentManagementService(repository, storage,
                new DocumentPreviewTokenProperties(), Clock.systemDefaultZone(), properties);
        byte[] content = jpeg("pending");

        assertThatThrownBy(() -> service.uploadAttachment("DOC-INBOUND-20260706-001", "称重交接", "HO-1",
                "pending.jpg", "image/jpeg", "PC 后台上传", "库管员", "无", content))
                .isInstanceOf(BusinessException.class).hasMessageContaining("审计记录结果未确认");
        var pending = repository.attachments().stream()
                .filter(attachment -> attachment.code().startsWith("ATT-UP-"))
                .findFirst().orElseThrow();
        assertThat(pending.status()).isEqualTo("待核对");
        DocumentManagementService restarted = new DocumentManagementService(repository, storage,
                new DocumentPreviewTokenProperties(), Clock.systemDefaultZone(), properties);
        assertThatThrownBy(() -> restarted.previewUrl(pending.code()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("待核对");
        assertThat(restarted.storageIntegrity().issues()).extracting("category").contains("PENDING_METADATA");

        var available = restarted.reconcilePendingAttachment(pending.code(), "系统管理员");

        assertThat(available.status()).isEqualTo("已上传");
        assertThat(restarted.content(pending.code()).content()).isEqualTo(content);
        assertThat(repository.auditEvents()).extracting("eventCode")
                .contains("DOC-AUD-RECONCILE-" + pending.code());
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
