package com.zhubao.erp.document.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.AttachmentContent;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.domain.AttachmentPreviewUrl;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentManagementSummary;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.StoredAttachmentObject;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DocumentManagementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentManagementRepository repository;
    private final AttachmentObjectStorage attachmentObjectStorage;
    private final AtomicInteger uploadSequence = new AtomicInteger(1);
    private final Map<String, String> uploadedStorageKeys = new ConcurrentHashMap<>();

    public DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage
    ) {
        this.repository = repository;
        this.attachmentObjectStorage = attachmentObjectStorage;
    }

    public DocumentManagementSummary summary() {
        List<EvidenceAttachment> attachments = repository.attachments();
        long missingEvidence = attachments.stream()
                .filter(attachment -> !attachment.status().equals("已关联")
                        && !attachment.status().equals("已归档")
                        && !attachment.status().equals("已上传"))
                .count();
        long manualFallbacks = attachments.stream()
                .filter(attachment -> !attachment.fallbackAction().equals("无"))
                .count();
        return new DocumentManagementSummary(
                repository.packages().size(),
                attachments.size(),
                (int) missingEvidence,
                (int) manualFallbacks,
                repository.auditEvents().size()
        );
    }

    public List<DocumentPackage> packages() {
        return repository.packages();
    }

    public List<EvidenceAttachment> attachments() {
        return repository.attachments();
    }

    public List<DocumentAuditEvent> auditEvents() {
        return repository.auditEvents();
    }

    public DocumentPackage savePackage(DocumentPackage documentPackage) {
        repository.savePackage(documentPackage);
        return documentPackage;
    }

    public EvidenceAttachmentUploadResult uploadAttachment(
            String documentCode,
            String businessType,
            String businessCode,
            String fileName,
            String mediaType,
            String source,
            String owner,
            String fallbackAction,
            byte[] content
    ) {
        if (content == null || content.length == 0) {
            throw new BusinessException("附件内容不能为空");
        }
        if (repository.packages().stream().noneMatch(documentPackage -> documentPackage.code().equals(documentCode))) {
            throw new BusinessException("文档包不存在：" + documentCode);
        }

        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String code = "ATT-UP-" + now.replace("-", "").replace(":", "").replace(" ", "-")
                + "-" + String.format("%03d", uploadSequence.getAndIncrement());
        String sanitizedFileName = sanitizeFileName(fileName);
        String safeMediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType;
        String safeFallbackAction = fallbackAction == null || fallbackAction.isBlank() ? "无" : fallbackAction;
        StoredAttachmentObject storedObject = attachmentObjectStorage.put(
                documentCode,
                code,
                sanitizedFileName,
                safeMediaType,
                content
        );

        EvidenceAttachment attachment = new EvidenceAttachment(
                code,
                documentCode,
                businessType,
                businessCode,
                sanitizedFileName,
                storedObject.mediaType(),
                storedObject.storageLocation(),
                defaultStoragePolicy(safeMediaType),
                source,
                "已上传",
                owner,
                storedObject.checksum(),
                now,
                safeFallbackAction,
                "green"
        );
        repository.saveAttachment(attachment);
        uploadedStorageKeys.put(code, storedObject.storageKey());
        repository.saveAuditEvent(new DocumentAuditEvent(
                now,
                "DOC-AUD-" + code,
                documentCode,
                "上传附件",
                owner,
                "上传 " + sanitizedFileName + " 并生成校验和 " + storedObject.checksum() + "。",
                "green"
        ));

        return new EvidenceAttachmentUploadResult(
                code,
                documentCode,
                sanitizedFileName,
                storedObject.mediaType(),
                storedObject.size(),
                storedObject.storageLocation(),
                storedObject.checksum(),
                now,
                previewContentUrl(code)
        );
    }

    public AttachmentPreviewUrl previewUrl(String code) {
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        return new AttachmentPreviewUrl(
                attachment.code(),
                attachment.fileName(),
                attachment.mediaType(),
                previewContentUrl(attachment.code()),
                LocalDateTime.now().plusMinutes(15).format(DATE_TIME_FORMATTER)
        );
    }

    public AttachmentContent content(String code) {
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        String storageKey = uploadedStorageKeys.get(code);
        if (storageKey == null) {
            storageKey = localStorageKey(attachment.storageLocation())
                    .orElseThrow(() -> new BusinessException("附件尚未进入本地对象存储：" + code));
        }
        byte[] content = attachmentObjectStorage.get(storageKey);
        return new AttachmentContent(
                attachment.code(),
                attachment.fileName(),
                attachment.mediaType(),
                content
        );
    }

    private Optional<EvidenceAttachment> findAttachment(String code) {
        return repository.attachments().stream()
                .filter(attachment -> attachment.code().equals(code))
                .findFirst();
    }

    private Optional<String> localStorageKey(String storageLocation) {
        if (storageLocation == null || !storageLocation.startsWith("local://")) {
            return Optional.empty();
        }
        String storageKey = storageLocation.substring("local://".length()).trim();
        if (storageKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(storageKey);
    }

    private String previewContentUrl(String code) {
        return "/api/documents/attachments/" + code + "/content?token=preview-demo";
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment.bin";
        }
        return fileName.replace("\\", "_").replace("/", "_").trim();
    }

    private String defaultStoragePolicy(String mediaType) {
        if (mediaType.startsWith("video/")) {
            return "hot 90d -> archive";
        }
        if (mediaType.equals("application/pdf")) {
            return "audit long-term";
        }
        return "hot 180d -> archive";
    }
}
