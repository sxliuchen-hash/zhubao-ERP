package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
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
import com.zhubao.erp.document.domain.StoredContentInventory;
import com.zhubao.erp.document.domain.StoredContentItem;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DocumentManagementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SIGNING_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final DocumentManagementRepository repository;
    private final AttachmentObjectStorage attachmentObjectStorage;
    private final DocumentPreviewTokenProperties previewTokenProperties;
    private final AttachmentStorageProperties storageProperties;
    private final BusinessDocumentRepository businessDocuments;
    private final Clock clock;
    private final AtomicInteger uploadSequence = new AtomicInteger(1);
    private final Map<String, String> uploadedStorageKeys = new ConcurrentHashMap<>();

    public DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage
    ) {
        this(repository, attachmentObjectStorage, new DocumentPreviewTokenProperties(), Clock.systemDefaultZone());
    }

    public DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties
    ) {
        this(repository, attachmentObjectStorage, previewTokenProperties, Clock.systemDefaultZone(), null, null);
    }

    public DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            AttachmentStorageProperties storageProperties
    ) {
        this(repository, attachmentObjectStorage, previewTokenProperties, Clock.systemDefaultZone(), storageProperties, null);
    }

    @Autowired
    public DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            AttachmentStorageProperties storageProperties,
            BusinessDocumentRepository businessDocuments
    ) {
        this(repository, attachmentObjectStorage, previewTokenProperties, Clock.systemDefaultZone(), storageProperties, businessDocuments);
    }

    DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            Clock clock
    ) {
        this(repository, attachmentObjectStorage, previewTokenProperties, clock, null, null);
    }

    DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            Clock clock,
            AttachmentStorageProperties storageProperties
    ) {
        this(repository, attachmentObjectStorage, previewTokenProperties, clock, storageProperties, null);
    }

    DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            Clock clock,
            AttachmentStorageProperties storageProperties,
            BusinessDocumentRepository businessDocuments
    ) {
        this.repository = repository;
        this.attachmentObjectStorage = attachmentObjectStorage;
        this.previewTokenProperties = previewTokenProperties == null ? new DocumentPreviewTokenProperties() : previewTokenProperties;
        this.storageProperties = storageProperties;
        this.businessDocuments = businessDocuments;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public DocumentManagementSummary summary() {
        List<EvidenceAttachment> attachments = repository.attachments();
        long missingEvidence = attachments.stream()
                .filter(attachment -> !attachment.status().equals("已关联")
                        && !attachment.status().equals("已归档")
                        && !attachment.status().equals("已上传")
                        && !attachment.status().equals("已下线"))
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

    /**
     * 汇总附件目录实际占用、所在磁盘余量，以及每条已登记内容。
     */
    public StoredContentInventory storageInventory() {
        Path root = storageRoot();
        List<StoredContentItem> items = repository.attachments().stream()
                .map(this::toStoredContentItem)
                .toList();
        long usedBytes = directorySize(root);
        DiskUsage disk = diskUsage(root);
        String provider = storageProperties == null || storageProperties.getProvider() == null
                || storageProperties.getProvider().isBlank()
                ? "local"
                : storageProperties.getProvider().trim();
        return new StoredContentInventory(
                provider,
                root.toAbsolutePath().normalize().toString(),
                items.size(),
                usedBytes,
                readableSize(usedBytes),
                disk.totalBytes(),
                disk.freeBytes(),
                disk.usedPercent(),
                items
        );
    }

    /**
     * 管理员下线一条存储内容：删除本地对象，台账保留为已下线。
     */
    public StoredContentItem retireStoredContent(String code, String operator) {
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        if ("已下线".equals(attachment.status())) {
            throw new BusinessException("内容已下线：" + code);
        }
        localStorageKey(attachment.storageLocation()).ifPresent(storageKey -> {
            attachmentObjectStorage.delete(storageKey);
            uploadedStorageKeys.remove(code);
        });
        String now = LocalDateTime.now(clock).format(DATE_TIME_FORMATTER);
        EvidenceAttachment retired = new EvidenceAttachment(
                attachment.code(),
                attachment.documentCode(),
                attachment.businessType(),
                attachment.businessCode(),
                attachment.fileName(),
                attachment.mediaType(),
                attachment.storageLocation(),
                attachment.storagePolicy(),
                attachment.source(),
                "已下线",
                attachment.owner(),
                attachment.checksum(),
                attachment.uploadedAt(),
                attachment.fallbackAction(),
                "gray",
                attachment.objectType()
        );
        repository.saveAttachment(retired);
        String actor = operator == null || operator.isBlank() ? "系统管理员" : operator.trim();
        repository.saveAuditEvent(new DocumentAuditEvent(
                now,
                "DOC-AUD-RETIRE-" + attachment.code(),
                attachment.documentCode(),
                "下线存储内容",
                actor,
                "下线 " + attachment.fileName() + "，并删除本地对象。",
                "amber"
        ));
        return toStoredContentItem(retired);
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
        String objectType = AttachmentOrigins.onlyHit(businessDocuments, businessCode);
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
                "green",
                objectType.isEmpty() ? null : objectType
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
        assertActive(attachment);
        PreviewToken previewToken = newPreviewToken(attachment.code());
        return new AttachmentPreviewUrl(
                attachment.code(),
                attachment.fileName(),
                attachment.mediaType(),
                previewContentUrl(attachment.code(), previewToken),
                displayExpiresAt(previewToken.expiresAtEpoch())
        );
    }

    public AttachmentContent previewContent(String code, long expiresAt, String token) {
        validatePreviewToken(code, expiresAt, token);
        return content(code);
    }

    public AttachmentContent content(String code) {
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        assertActive(attachment);
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

    private void assertActive(EvidenceAttachment attachment) {
        if ("已下线".equals(attachment.status())) throw new BusinessException("内容已下线：" + attachment.code());
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
        return previewContentUrl(code, newPreviewToken(code));
    }

    private String previewContentUrl(String code, PreviewToken previewToken) {
        return "/api/documents/attachments/" + code
                + "/content?expiresAt=" + previewToken.expiresAtEpoch()
                + "&token=" + previewToken.signature();
    }

    private PreviewToken newPreviewToken(String code) {
        long expiresAtEpoch = Instant.now(clock)
                .plus(previewTokenProperties.ttl())
                .getEpochSecond();
        return new PreviewToken(expiresAtEpoch, signPreviewToken(code, expiresAtEpoch));
    }

    private void validatePreviewToken(String code, long expiresAtEpoch, String token) {
        if (expiresAtEpoch <= Instant.now(clock).getEpochSecond()) {
            throw new BusinessException("附件预览链接已过期");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException("附件预览令牌不能为空");
        }
        String expected = signPreviewToken(code, expiresAtEpoch);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BusinessException("附件预览令牌无效");
        }
    }

    private String signPreviewToken(String code, long expiresAtEpoch) {
        try {
            Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
            mac.init(new SecretKeySpec(
                    previewTokenProperties.signingSecret().getBytes(StandardCharsets.UTF_8),
                    SIGNING_ALGORITHM
            ));
            String payload = code + ":" + expiresAtEpoch;
            return TOKEN_ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign attachment preview token", ex);
        }
    }

    private String displayExpiresAt(long expiresAtEpoch) {
        ZoneId zone = clock.getZone() == null ? ZoneId.systemDefault() : clock.getZone();
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(expiresAtEpoch), zone).format(DATE_TIME_FORMATTER);
    }

    private record PreviewToken(long expiresAtEpoch, String signature) {
    }

    private StoredContentItem toStoredContentItem(EvidenceAttachment attachment) {
        long sizeBytes = sizeBytes(attachment);
        return new StoredContentItem(
                attachment.code(),
                attachment.fileName(),
                attachment.businessType(),
                attachment.businessCode(),
                attachment.owner(),
                attachment.storageLocation(),
                attachment.mediaType(),
                attachment.status(),
                attachment.uploadedAt(),
                sizeBytes,
                readableSize(sizeBytes)
        );
    }

    private long sizeBytes(EvidenceAttachment attachment) {
        Optional<String> storageKey = localStorageKey(attachment.storageLocation());
        if (storageKey.isEmpty()) {
            return -1;
        }
        Path root = storageRoot().toAbsolutePath().normalize();
        Path file = root.resolve(storageKey.get()).normalize();
        if (!file.startsWith(root)) {
            return -1;
        }
        try {
            return Files.isRegularFile(file) ? Files.size(file) : -1;
        } catch (IOException exception) {
            return -1;
        }
    }

    private Path storageRoot() {
        if (storageProperties != null
                && storageProperties.getLocalRoot() != null
                && !storageProperties.getLocalRoot().isBlank()) {
            return Path.of(storageProperties.getLocalRoot());
        }
        return Path.of("target/zhubao-evidence");
    }

    private long directorySize(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return 0;
        }
        long total = 0;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                try {
                    total += Files.size(path);
                } catch (IOException exception) {
                    // 单个文件读不到时跳过，其余占用仍然统计。
                }
            }
        } catch (IOException exception) {
            return total;
        }
        return total;
    }

    private DiskUsage diskUsage(Path root) {
        try {
            Path probe = root.toAbsolutePath().normalize();
            while (probe != null && !Files.exists(probe)) {
                probe = probe.getParent();
            }
            if (probe == null) {
                probe = Path.of("").toAbsolutePath();
            }
            var store = Files.getFileStore(probe);
            long total = Math.max(store.getTotalSpace(), 0);
            long free = Math.max(store.getUsableSpace(), 0);
            long used = Math.max(total - free, 0);
            int percent = total == 0 ? 0 : (int) Math.round(used * 100.0 / total);
            return new DiskUsage(total, free, percent);
        } catch (IOException exception) {
            return new DiskUsage(0, 0, 0);
        }
    }

    private String readableSize(long bytes) {
        if (bytes < 0) {
            return "未落本地";
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f MiB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L) {
            return String.format(Locale.ROOT, "%.2f KiB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private record DiskUsage(long totalBytes, long freeBytes, int usedPercent) {
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
