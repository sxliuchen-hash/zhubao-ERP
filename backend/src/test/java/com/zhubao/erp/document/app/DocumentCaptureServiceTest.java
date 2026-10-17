package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
import com.zhubao.erp.document.infra.DocumentCaptureProperties;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.document.infra.LocalAttachmentObjectStorage;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class DocumentCaptureServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesUnpackVideoOnMotherOrderAndDeduplicatesChecksum() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(motherOrder());
        InMemoryDocumentManagementRepository repository = new InMemoryDocumentManagementRepository();
        DocumentCaptureService service = service(documents, repository, 8, 32);
        OperatorContext receiver = new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL);
        byte[] video = "unpack-video".getBytes(StandardCharsets.UTF_8);

        EvidenceAttachmentUploadResult uploaded = service.capture(
                "mother",
                "MD-CAP-001",
                "UNPACK",
                "unpack.mp4",
                "video/mp4",
                new java.io.ByteArrayInputStream(video),
                video.length,
                receiver
        );
        EvidenceAttachmentUploadResult again = service.capture(
                "mother",
                "MD-CAP-001",
                "UNPACK",
                "unpack.mp4",
                "video/mp4",
                new java.io.ByteArrayInputStream(video),
                video.length,
                receiver
        );

        assertThat(uploaded.code()).startsWith("ATT-CAP-");
        assertThat(again.code()).isEqualTo(uploaded.code());
        assertThat(service.list("MD-CAP-001")).hasSize(1);
        assertThat(service.list("MD-CAP-001").get(0).scene()).isEqualTo("拆包");
        assertThat(service.list("MD-CAP-001").get(0).previewUrl()).contains("expiresAt=");
    }

    @Test
    void rejectsOversizedVideoAndOutOfScopeCustomer() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(motherOrder());
        DocumentCaptureService service = service(documents, new InMemoryDocumentManagementRepository(), 8, 4);
        OperatorContext receiver = new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL);
        byte[] video = "12345".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.capture(
                "mother",
                "MD-CAP-001",
                "UNPACK",
                "big.mp4",
                "video/mp4",
                new java.io.ByteArrayInputStream(video),
                video.length,
                receiver
        )).isInstanceOf(BusinessException.class).hasMessageContaining("大小限制");

        OperatorContext scoped = new OperatorContext(
                "收货员 何琴",
                RoleCode.RECEIVER,
                DataScope.CUSTOMER_RANGE,
                false,
                "",
                List.of("雅辰饰品")
        );
        assertThatThrownBy(() -> service.capture(
                "mother",
                "MD-CAP-001",
                "UNPACK",
                "unpack.mp4",
                "video/mp4",
                new java.io.ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)),
                2,
                scoped
        )).isInstanceOf(BusinessException.class).hasMessageContaining("客户范围");
    }

    @Test
    void uncertainMetadataWriteKeepsCaptureOriginalForReconciliation() throws Exception {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(motherOrder());
        InMemoryDocumentManagementRepository repository = spy(new InMemoryDocumentManagementRepository());
        doThrow(new IllegalStateException("提交结果未知"))
                .when(repository).saveAttachment(argThat(attachment -> attachment.code().startsWith("ATT-CAP-")));
        DocumentCaptureService service = service(documents, repository, 8, 32);
        byte[] video = "unpack-video".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.capture("mother", "MD-CAP-001", "UNPACK", "unpack.mp4",
                "video/mp4", new java.io.ByteArrayInputStream(video), video.length,
                new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原件已保存")
                .hasMessageContaining("核对后处理");
        try (var paths = Files.walk(tempDir)) {
            assertThat(paths.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    private DocumentCaptureService service(
            InMemoryBusinessDocumentRepository documents,
            InMemoryDocumentManagementRepository repository,
            long photoMax,
            long videoMax
    ) {
        AttachmentStorageProperties storageProperties = new AttachmentStorageProperties();
        storageProperties.setLocalRoot(tempDir.toString());
        DocumentCaptureProperties captureProperties = new DocumentCaptureProperties();
        captureProperties.setPhotoMaxBytes(photoMax);
        captureProperties.setVideoMaxBytes(videoMax);
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(storageProperties);
        DocumentManagementService documentsService = new DocumentManagementService(repository, storage);
        return new DocumentCaptureService(repository, storage, documentsService, documents, captureProperties);
    }

    private MotherOrderRecord motherOrder() {
        return new MotherOrderRecord(
                "MD-CAP-001",
                1001L,
                1001L,
                "MAT-MOBILE",
                "2026-10-08",
                "顺丰",
                "SF1048329001",
                "",
                "收货员 何琴",
                "收货员 何琴",
                "18K 金戒托",
                "CUSTOMER",
                true,
                "RAW-CUSTOMER",
                new BigDecimal("12"),
                "件",
                new BigDecimal("18.500"),
                "RAW_WAITING",
                "STK-CAP-001",
                "手机收货",
                null,
                "",
                "收货员 何琴",
                "SF1048329001",
                "手机收货生成母单",
                "INBOUND_CONFIRMED",
                "2026-10-08T10:00:00",
                "戒托抛光",
                "2026-10-20",
                "恒信珠宝"
        );
    }
}
