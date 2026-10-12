package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentCaptureView;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.domain.EvidenceLinkKey;
import com.zhubao.erp.document.domain.StoredAttachmentObject;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.infra.DocumentCaptureProperties;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手机采集。按单号自动建证据包，照片和短视频流式落盘，相同校验和不存第二份。
 * 场景写在本次采集说明里；跨场景引用由证据关联另记，不用第一次场景覆盖。
 */
@Service
public class DocumentCaptureService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DocumentManagementRepository repository;
    private final AttachmentObjectStorage storage;
    private final DocumentManagementService documentManagementService;
    private final BusinessDocumentRepository businessDocumentRepository;
    private final DocumentCaptureProperties properties;
    private final AtomicInteger sequence = new AtomicInteger();

    public DocumentCaptureService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage storage,
            DocumentManagementService documentManagementService,
            BusinessDocumentRepository businessDocumentRepository,
            DocumentCaptureProperties properties
    ) {
        this.repository = repository;
        this.storage = storage;
        this.documentManagementService = documentManagementService;
        this.businessDocumentRepository = businessDocumentRepository;
        this.properties = properties;
    }

    public EvidenceAttachmentUploadResult capture(
            String objectType,
            String businessCode,
            String scene,
            String fileName,
            String mediaType,
            InputStream input,
            long declaredSize,
            OperatorContext operator
    ) {
        return store(objectType, businessCode, scene, fileName, mediaType, input, declaredSize, operator, false);
    }

    /**
     * 确认单据后再落盘。无法唯一判定来源时不写文件，避免产生不能绑定的新附件。
     */
    public EvidenceAttachmentUploadResult storeVerified(
            String objectType,
            String businessCode,
            String scene,
            String fileName,
            String mediaType,
            InputStream input,
            long declaredSize,
            OperatorContext operator
    ) {
        return store(objectType, businessCode, scene, fileName, mediaType, input, declaredSize, operator, true);
    }

    private EvidenceAttachmentUploadResult store(
            String objectType,
            String businessCode,
            String scene,
            String fileName,
            String mediaType,
            InputStream input,
            long declaredSize,
            OperatorContext operator,
            boolean requireVerified
    ) {
        String canonicalType = EvidenceCodes.canonicalObjectType(objectType);
        if (canonicalType.isEmpty()) {
            throw new BusinessException("不支持的单据类型");
        }
        String code = required(businessCode, "单号不能为空");
        String sceneCode = required(scene, "采集场景不能为空").toUpperCase(Locale.ROOT);
        String sceneLabel = EvidenceCodes.sceneLabel(sceneCode);
        if (sceneLabel == null) {
            throw new BusinessException("不支持的采集场景");
        }
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再采集");
        }
        if (EvidenceCodes.MOTHER_ORDER.equals(canonicalType)) {
            MotherOrderRecord motherOrder = businessDocumentRepository.findMotherOrderByCode(code)
                    .orElseThrow(() -> new BusinessException("母单不存在：" + code));
            assertCustomerAllowed(operator, motherOrder);
        }
        boolean video = isVideo(mediaType);
        boolean photo = isPhoto(mediaType);
        if (!video && !photo) {
            throw new BusinessException("只接受 jpg、png、mp4、webm");
        }
        String origin = AttachmentOrigins.uniqueMatch(businessDocumentRepository, canonicalType, code);
        if (requireVerified && origin.isEmpty()) {
            throw new BusinessException("无法确认这份文件属于哪张单据");
        }
        long maxBytes = video ? properties.getVideoMaxBytes() : properties.getPhotoMaxBytes();
        String packageCode = "DOC-" + code;
        ensurePackage(packageCode, code, sceneLabel, operator.operator());
        String attachmentCode = "ATT-CAP-"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                + "-" + sequence.incrementAndGet();
        StoredAttachmentObject stored = storage.putStream(
                packageCode,
                attachmentCode,
                sanitizeFileName(fileName, video),
                mediaType,
                input,
                declaredSize,
                maxBytes
        );
        try {
            EvidenceAttachment existing = repository.attachments().stream()
                    .filter(attachment -> sameFile(attachment, code, stored.checksum(), origin))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                storage.delete(stored.storageKey());
                EvidenceAttachment kept = backfillOrigin(existing, origin);
                return resultOf(kept, stored.size());
            }
            String now = LocalDateTime.now().format(DATE_TIME);
            EvidenceAttachment attachment = new EvidenceAttachment(
                    attachmentCode,
                    packageCode,
                    sceneLabel,
                    code,
                    sanitizeFileName(fileName, video),
                    stored.mediaType(),
                    stored.storageLocation(),
                    video ? "hot 90d -> archive" : "hot 180d -> archive",
                    "手机采集",
                    "已上传",
                    operator.operator(),
                    stored.checksum(),
                    now,
                    "无",
                    "green",
                    origin.isEmpty() ? null : origin
            );
            try {
                repository.saveAttachment(attachment);
            } catch (RuntimeException ex) {
                storage.delete(stored.storageKey());
                throw new BusinessException("证据元数据没有保存，可以重新上传");
            }
            try {
                repository.saveAuditEvent(new DocumentAuditEvent(
                        now,
                        "DOC-AUD-" + attachmentCode,
                        packageCode,
                        "手机采集",
                        operator.operator(),
                        "上传 " + attachment.fileName() + "，校验和 " + stored.checksum(),
                        "green"
                ));
            } catch (RuntimeException ex) {
                throw new BusinessException("文件已上传，审计没有记下，可以重试");
            }
            return resultOf(attachment, stored.size());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            storage.delete(stored.storageKey());
            throw new BusinessException("证据元数据没有保存，可以重新上传");
        }
    }

    public List<DocumentCaptureView> list(String businessCode) {
        String code = required(businessCode, "单号不能为空");
        return repository.attachments().stream()
                .filter(attachment -> code.equals(attachment.businessCode()))
                .filter(attachment -> "手机采集".equals(attachment.source()))
                .filter(attachment -> !"已下线".equals(attachment.status()))
                .map(this::viewOf)
                .toList();
    }

    private boolean sameFile(EvidenceAttachment attachment, String businessCode, String checksum, String origin) {
        if (!businessCode.equals(attachment.businessCode()) || !checksum.equals(attachment.checksum())) {
            return false;
        }
        String current = EvidenceLinkKey.text(attachment.objectType());
        String next = EvidenceLinkKey.text(origin);
        return current.isEmpty() || current.equals(next);
    }

    private EvidenceAttachment backfillOrigin(EvidenceAttachment existing, String origin) {
        if (!EvidenceLinkKey.text(existing.objectType()).isEmpty() || EvidenceLinkKey.text(origin).isEmpty()) {
            return existing;
        }
        EvidenceAttachment updated = new EvidenceAttachment(
                existing.code(),
                existing.documentCode(),
                existing.businessType(),
                existing.businessCode(),
                existing.fileName(),
                existing.mediaType(),
                existing.storageLocation(),
                existing.storagePolicy(),
                existing.source(),
                existing.status(),
                existing.owner(),
                existing.checksum(),
                existing.uploadedAt(),
                existing.fallbackAction(),
                existing.tone(),
                origin
        );
        repository.saveAttachment(updated);
        return updated;
    }

    private void ensurePackage(String packageCode, String businessCode, String sceneLabel, String operator) {
        boolean exists = repository.packages().stream().anyMatch(item -> packageCode.equals(item.code()));
        if (exists) {
            return;
        }
        String now = LocalDateTime.now().format(DATE_TIME);
        repository.savePackage(new DocumentPackage(
                packageCode,
                businessCode + " 手机采集",
                sceneLabel,
                businessCode,
                operator,
                "手机采集的照片或短视频",
                1,
                0,
                "采集中",
                "试运行文件留在本机目录",
                now,
                "green"
        ));
    }

    private void assertCustomerAllowed(OperatorContext operator, MotherOrderRecord motherOrder) {
        if (operator.dataScope() == DataScope.ALL || operator.customerScope().isEmpty()) {
            return;
        }
        String customerId = motherOrder.customerId() == null ? "" : String.valueOf(motherOrder.customerId());
        boolean allowed = operator.customerScope().stream().anyMatch(item ->
                item.equals(motherOrder.customerName())
                        || item.equals(customerId)
                        || item.equals("客户#" + customerId));
        if (!allowed) {
            throw new BusinessException("单号不在当前账号的客户范围内");
        }
    }

    private EvidenceAttachmentUploadResult resultOf(EvidenceAttachment attachment, long size) {
        return new EvidenceAttachmentUploadResult(
                attachment.code(),
                attachment.documentCode(),
                attachment.fileName(),
                attachment.mediaType(),
                size,
                attachment.storageLocation(),
                attachment.checksum(),
                attachment.uploadedAt(),
                documentManagementService.previewUrl(attachment.code()).url()
        );
    }

    private DocumentCaptureView viewOf(EvidenceAttachment attachment) {
        return new DocumentCaptureView(
                attachment.code(),
                attachment.businessCode(),
                attachment.businessType(),
                attachment.fileName(),
                attachment.mediaType(),
                attachment.checksum(),
                attachment.uploadedAt(),
                attachment.owner(),
                documentManagementService.previewUrl(attachment.code()).url()
        );
    }

    private boolean isVideo(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.equals("video/mp4") || normalized.equals("video/webm");
    }

    private boolean isPhoto(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.equals("image/jpeg") || normalized.equals("image/jpg") || normalized.equals("image/png");
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String sanitizeFileName(String fileName, boolean video) {
        String fallback = video ? "capture.mp4" : "capture.jpg";
        if (fileName == null || fileName.isBlank()) {
            return fallback;
        }
        return fileName.replace("\\", "_").replace("/", "_").trim();
    }
}
