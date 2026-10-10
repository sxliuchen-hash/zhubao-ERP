package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentCaptureView;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.StoredAttachmentObject;
import com.zhubao.erp.document.infra.DocumentCaptureProperties;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手机采集。按单号自动建证据包，照片和短视频流式落盘，相同校验和不存第二份。
 */
@Service
public class DocumentCaptureService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> OBJECT_TYPES = Set.of("mother", "work_order", "outbound", "rework");
    private static final Map<String, String> SCENES = Map.of(
            "UNPACK", "拆包",
            "WEIGH", "称重",
            "DEFECT", "缺陷",
            "FINISH", "成品"
    );

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
        String type = required(objectType, "单据类型不能为空");
        if (!OBJECT_TYPES.contains(type)) {
            throw new BusinessException("不支持的单据类型");
        }
        String code = required(businessCode, "单号不能为空");
        String sceneCode = required(scene, "采集场景不能为空").toUpperCase(Locale.ROOT);
        String sceneLabel = SCENES.get(sceneCode);
        if (sceneLabel == null) {
            throw new BusinessException("不支持的采集场景");
        }
        if (operator == null || operator.operator() == null || operator.operator().isBlank()) {
            throw new BusinessException("请先登录后再采集");
        }
        if ("mother".equals(type)) {
            MotherOrderRecord motherOrder = businessDocumentRepository.findMotherOrderByCode(code)
                    .orElseThrow(() -> new BusinessException("母单不存在：" + code));
            assertCustomerAllowed(operator, motherOrder);
        }
        boolean video = isVideo(mediaType);
        boolean photo = isPhoto(mediaType);
        if (!video && !photo) {
            throw new BusinessException("只接受 jpg、png、mp4、webm");
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
        EvidenceAttachment existing = repository.attachments().stream()
                .filter(attachment -> code.equals(attachment.businessCode()) && stored.checksum().equals(attachment.checksum()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            storage.delete(stored.storageKey());
            return resultOf(existing, stored.size());
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
                "green"
        );
        repository.saveAttachment(attachment);
        repository.saveAuditEvent(new DocumentAuditEvent(
                now,
                "DOC-AUD-" + attachmentCode,
                packageCode,
                "手机采集",
                operator.operator(),
                "上传 " + attachment.fileName() + "，校验和 " + stored.checksum(),
                "green"
        ));
        return resultOf(attachment, stored.size());
    }

    public List<DocumentCaptureView> list(String businessCode) {
        String code = required(businessCode, "单号不能为空");
        return repository.attachments().stream()
                .filter(attachment -> code.equals(attachment.businessCode()))
                .filter(attachment -> "手机采集".equals(attachment.source()))
                .map(this::viewOf)
                .toList();
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
