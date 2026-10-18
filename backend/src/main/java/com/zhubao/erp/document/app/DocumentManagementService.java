package com.zhubao.erp.document.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.AttachmentContent;
import com.zhubao.erp.document.domain.AttachmentIntegrityIssue;
import com.zhubao.erp.document.domain.AttachmentIntegrityReport;
import com.zhubao.erp.document.domain.AttachmentMediaPolicy;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.domain.AttachmentStreamContent;
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
import com.zhubao.erp.document.infra.DocumentCaptureProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentManagementService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SIGNING_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String PURPOSE_PREVIEW = "preview";
    private static final String PURPOSE_DOWNLOAD = "download";
    private static final String RESOLUTION_ORIGINAL = "original";

    private final DocumentManagementRepository repository;
    private final AttachmentObjectStorage attachmentObjectStorage;
    private final DocumentPreviewTokenProperties previewTokenProperties;
    private final AttachmentStorageProperties storageProperties;
    private final BusinessDocumentRepository businessDocuments;
    private final DocumentCaptureProperties captureProperties;
    private final Clock clock;
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

    public DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            AttachmentStorageProperties storageProperties,
            BusinessDocumentRepository businessDocuments
    ) {
        this(repository, attachmentObjectStorage, previewTokenProperties, Clock.systemDefaultZone(), storageProperties, businessDocuments);
    }

    @Autowired
    public DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            AttachmentStorageProperties storageProperties,
            BusinessDocumentRepository businessDocuments,
            DocumentCaptureProperties captureProperties
    ) {
        this(repository, attachmentObjectStorage, previewTokenProperties, Clock.systemDefaultZone(),
                storageProperties, businessDocuments, captureProperties);
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
        this(repository, attachmentObjectStorage, previewTokenProperties, clock,
                storageProperties, businessDocuments, null);
    }

    private DocumentManagementService(
            DocumentManagementRepository repository,
            AttachmentObjectStorage attachmentObjectStorage,
            DocumentPreviewTokenProperties previewTokenProperties,
            Clock clock,
            AttachmentStorageProperties storageProperties,
            BusinessDocumentRepository businessDocuments,
            DocumentCaptureProperties captureProperties
    ) {
        this.repository = repository;
        this.attachmentObjectStorage = attachmentObjectStorage;
        this.previewTokenProperties = previewTokenProperties == null ? new DocumentPreviewTokenProperties() : previewTokenProperties;
        this.storageProperties = storageProperties;
        this.businessDocuments = businessDocuments;
        this.captureProperties = captureProperties == null ? new DocumentCaptureProperties() : captureProperties;
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
     * 逐一核对活跃元数据、原件和 SHA-256，并列出无元数据文件及未发布的临时文件。
     */
    public AttachmentIntegrityReport storageIntegrity() {
        Path root = storageRoot().toAbsolutePath().normalize();
        List<EvidenceAttachment> attachments = repository.attachments();
        List<AttachmentIntegrityIssue> issues = new ArrayList<>();
        Map<String, EvidenceAttachment> registered = new HashMap<>();
        for (EvidenceAttachment attachment : attachments) {
            Optional<String> key = localStorageKey(attachment.storageLocation());
            if (key.isEmpty()) {
                continue;
            }
            registered.put(key.get(), attachment);
            if ("已下线".equals(attachment.status())) {
                continue;
            }
            if ("待核对".equals(attachment.status())) {
                issues.add(new AttachmentIntegrityIssue("PENDING_METADATA", attachment.code(), key.get(), "附件等待原件与审计核对"));
            }
            Path file = root.resolve(key.get()).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                issues.add(new AttachmentIntegrityIssue("MISSING_ORIGINAL", attachment.code(), key.get(), "活跃附件原件不存在"));
                continue;
            }
            try {
                String actual = "sha256:" + checksum(file);
                if (!actual.equalsIgnoreCase(attachment.checksum())) {
                    issues.add(new AttachmentIntegrityIssue("CHECKSUM_MISMATCH", attachment.code(), key.get(), "原件 SHA-256 与元数据不一致"));
                }
            } catch (IOException exception) {
                issues.add(new AttachmentIntegrityIssue("READ_ERROR", attachment.code(), key.get(), "原件无法读取或计算校验和"));
            }
        }
        int originalCount = 0;
        if (!Files.isDirectory(root)) {
            issues.add(new AttachmentIntegrityIssue("ROOT_MISSING", "", "", "附件存储目录不存在"));
        } else {
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    String key = root.relativize(file).toString().replace('\\', '/');
                    if (file.getFileName().toString().endsWith(".pending")) {
                        issues.add(new AttachmentIntegrityIssue("PENDING_OBJECT", "", key, "上传中断遗留临时文件"));
                    } else {
                        originalCount++;
                        if (!registered.containsKey(key)) {
                            issues.add(new AttachmentIntegrityIssue("UNREGISTERED_ORIGINAL", "", key, "原件没有附件元数据"));
                        }
                    }
                }
            } catch (IOException exception) {
                issues.add(new AttachmentIntegrityIssue("SCAN_ERROR", "", "", "附件目录无法完整扫描"));
            }
        }
        return new AttachmentIntegrityReport(LocalDateTime.now(clock).format(DATE_TIME_FORMATTER),
                attachments.size(), originalCount, issues);
    }

    @Scheduled(cron = "${zhubao.documents.integrity.cron:0 0 3 * * *}")
    public void checkStorageIntegrityOnSchedule() {
        try {
            AttachmentIntegrityReport report = storageIntegrity();
            if (!report.issues().isEmpty()) {
                LOGGER.warn("附件对账发现 {} 项异常，请在管理员存储完整性接口核对", report.issues().size());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("附件定时对账失败", exception);
        }
    }

    private String checksum(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 管理员下线一条存储内容：先关闭访问，保留原件供受控恢复。
     */
    public StoredContentItem retireStoredContent(String code, String operator) {
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        if ("已下线".equals(attachment.status())) {
            throw new BusinessException("内容已下线：" + code);
        }
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
        uploadedStorageKeys.remove(code);
        String actor = operator == null || operator.isBlank() ? "系统管理员" : operator.trim();
        try {
            repository.saveAuditEvent(new DocumentAuditEvent(
                    now,
                    "DOC-AUD-RETIRE-" + attachment.code(),
                    attachment.documentCode(),
                    "下线存储内容",
                    actor,
                    "下线 " + attachment.fileName() + "，原件保留供受控恢复。",
                    "amber"
            ));
        } catch (RuntimeException exception) {
            throw new BusinessException("附件 " + code + " 已下线且原件保留，审计记录结果未确认；请核对后处理");
        }
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
        return uploadAttachment(documentCode, businessType, businessCode, fileName, mediaType,
                source, owner, fallbackAction, new ByteArrayInputStream(content), content.length);
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
            InputStream input,
            long declaredSize
    ) {
        if (repository.packages().stream().noneMatch(documentPackage -> documentPackage.code().equals(documentCode))) {
            throw new BusinessException("文档包不存在：" + documentCode);
        }

        String now = LocalDateTime.now(clock).format(DATE_TIME_FORMATTER);
        String code = "ATT-UP-" + now.replace("-", "").replace(":", "").replace(" ", "-")
                + "-" + UUID.randomUUID().toString().replace("-", "");
        String sanitizedFileName = sanitizeFileName(fileName);
        String safeMediaType = AttachmentMediaPolicy.canonicalType(mediaType, true);
        String safeFallbackAction = fallbackAction == null || fallbackAction.isBlank() ? "无" : fallbackAction;
        String objectType = AttachmentOrigins.onlyHit(businessDocuments, businessCode);
        StoredAttachmentObject storedObject = attachmentObjectStorage.putStream(
                documentCode,
                code,
                sanitizedFileName,
                safeMediaType,
                input,
                declaredSize,
                AttachmentMediaPolicy.isVideo(safeMediaType)
                        ? captureProperties.getVideoMaxBytes() : captureProperties.getPhotoMaxBytes()
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
                "待核对",
                owner,
                storedObject.checksum(),
                now,
                safeFallbackAction,
                "amber",
                objectType.isEmpty() ? null : objectType
        );
        try {
            repository.saveAttachment(attachment);
        } catch (RuntimeException ex) {
            throw new BusinessException("附件原件已保存，元数据结果未确认；请按编号 " + code + " 核对后处理");
        }
        uploadedStorageKeys.put(code, storedObject.storageKey());
        try {
            repository.saveAuditEvent(new DocumentAuditEvent(
                    now,
                    "DOC-AUD-" + code,
                    documentCode,
                    "上传附件",
                    owner,
                    "上传 " + sanitizedFileName + " 并生成校验和 " + storedObject.checksum() + "。",
                    "green"
            ));
        } catch (RuntimeException ex) {
            throw new BusinessException("附件 " + code + " 已保存，审计记录结果未确认；请核对后处理");
        }
        try {
            repository.saveAttachment(attachment.withStatus("已上传", "green"));
        } catch (RuntimeException ex) {
            throw new BusinessException("附件 " + code + " 已登记待核对，启用结果未确认；请核对后处理");
        }

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
        return previewUrl(code, PURPOSE_PREVIEW);
    }

    public AttachmentPreviewUrl previewUrl(String code, String purpose) {
        String accessPurpose = requiredPurpose(purpose);
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        assertActive(attachment);
        PreviewToken previewToken = newPreviewToken(attachment.code(), accessPurpose);
        return new AttachmentPreviewUrl(
                attachment.code(),
                attachment.fileName(),
                attachment.mediaType(),
                previewContentUrl(attachment.code(), accessPurpose, previewToken),
                displayExpiresAt(previewToken.expiresAtEpoch())
        );
    }

    public AttachmentContent previewContent(String code, long expiresAt, String token) {
        return previewContent(code, expiresAt, token, PURPOSE_PREVIEW, RESOLUTION_ORIGINAL);
    }

    public AttachmentContent previewContent(String code, long expiresAt, String token, String purpose, String resolution) {
        String accessPurpose = requiredPurpose(purpose);
        if (!RESOLUTION_ORIGINAL.equals(resolution)) {
            throw new BusinessException("附件清晰度不支持");
        }
        validatePreviewToken(code, expiresAt, token, accessPurpose, resolution);
        return content(code);
    }

    public AttachmentStreamContent previewContentStream(
            String code, long expiresAt, String token, String purpose, String resolution
    ) {
        String accessPurpose = requiredPurpose(purpose);
        if (!RESOLUTION_ORIGINAL.equals(resolution)) {
            throw new BusinessException("附件清晰度不支持");
        }
        validatePreviewToken(code, expiresAt, token, accessPurpose, resolution);
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        assertActive(attachment);
        String storageKey = storageKeyOf(attachment);
        long size = attachmentObjectStorage.size(storageKey);
        byte[] header;
        try (InputStream input = attachmentObjectStorage.open(storageKey)) {
            header = input.readNBytes(12);
        } catch (IOException exception) {
            throw new BusinessException("附件读取本地存储失败：" + exception.getMessage());
        }
        return new AttachmentStreamContent(code, attachment.fileName(), attachment.mediaType(), size, header,
                (offset, length, output) -> writeContentRange(code, storageKey, offset, length, output));
    }

    private void writeContentRange(String code, String storageKey, long offset, long length, OutputStream output) throws IOException {
        EvidenceAttachment current = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        assertActive(current);
        try (InputStream input = attachmentObjectStorage.open(storageKey)) {
            input.skipNBytes(offset);
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read <= 0) {
                    throw new IOException("附件读取中断");
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    public AttachmentContent content(String code) {
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        assertActive(attachment);
        String storageKey = storageKeyOf(attachment);
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
        if ("待核对".equals(attachment.status())) throw new BusinessException("附件待核对，暂不可访问：" + attachment.code());
    }

    private String storageKeyOf(EvidenceAttachment attachment) {
        String storageKey = uploadedStorageKeys.get(attachment.code());
        if (storageKey != null) {
            return storageKey;
        }
        return localStorageKey(attachment.storageLocation())
                .orElseThrow(() -> new BusinessException("附件尚未进入本地对象存储：" + attachment.code()));
    }

    /**
     * 管理员核实原件校验和后，补记审计并启用中断的上传。
     */
    public EvidenceAttachment reconcilePendingAttachment(String code, String operator) {
        EvidenceAttachment attachment = findAttachment(code)
                .orElseThrow(() -> new BusinessException("附件不存在：" + code));
        if (!"待核对".equals(attachment.status())) {
            throw new BusinessException("附件不处于待核对状态：" + code);
        }
        String key = localStorageKey(attachment.storageLocation())
                .orElseThrow(() -> new BusinessException("附件没有本地原件：" + code));
        Path root = storageRoot().toAbsolutePath().normalize();
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException("附件原件不存在：" + code);
        }
        try {
            if (!("sha256:" + checksum(file)).equalsIgnoreCase(attachment.checksum())) {
                throw new BusinessException("附件原件校验和不一致：" + code);
            }
        } catch (IOException exception) {
            throw new BusinessException("附件原件无法核验：" + code);
        }
        String actor = operator == null || operator.isBlank() ? "系统管理员" : operator.trim();
        repository.saveAuditEvent(new DocumentAuditEvent(
                LocalDateTime.now(clock).format(DATE_TIME_FORMATTER),
                "DOC-AUD-RECONCILE-" + code,
                attachment.documentCode(),
                "核对附件原件",
                actor,
                "核对原件 SHA-256 后启用附件 " + code,
                "green"
        ));
        EvidenceAttachment available = attachment.withStatus("已上传", "green");
        repository.saveAttachment(available);
        return available;
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
        return previewContentUrl(code, PURPOSE_PREVIEW, newPreviewToken(code, PURPOSE_PREVIEW));
    }

    private String previewContentUrl(String code, String purpose, PreviewToken previewToken) {
        return "/api/documents/attachments/" + code
                + "/content?expiresAt=" + previewToken.expiresAtEpoch()
                + "&purpose=" + purpose
                + "&resolution=" + RESOLUTION_ORIGINAL
                + "&token=" + previewToken.signature();
    }

    private PreviewToken newPreviewToken(String code, String purpose) {
        long expiresAtEpoch = Instant.now(clock)
                .plus(previewTokenProperties.ttl())
                .getEpochSecond();
        return new PreviewToken(expiresAtEpoch, signPreviewToken(code, expiresAtEpoch, purpose, RESOLUTION_ORIGINAL));
    }

    private void validatePreviewToken(String code, long expiresAtEpoch, String token, String purpose, String resolution) {
        if (expiresAtEpoch <= Instant.now(clock).getEpochSecond()) {
            throw new BusinessException("附件预览链接已过期");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException("附件预览令牌不能为空");
        }
        String expected = signPreviewToken(code, expiresAtEpoch, purpose, resolution);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BusinessException("附件预览令牌无效");
        }
    }

    private String signPreviewToken(String code, long expiresAtEpoch, String purpose, String resolution) {
        try {
            Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
            mac.init(new SecretKeySpec(
                    previewTokenProperties.signingSecret().getBytes(StandardCharsets.UTF_8),
                    SIGNING_ALGORITHM
            ));
            String payload = code + ":" + expiresAtEpoch + ":" + purpose + ":" + resolution;
            return TOKEN_ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign attachment preview token", ex);
        }
    }

    private String requiredPurpose(String purpose) {
        if (PURPOSE_PREVIEW.equals(purpose) || PURPOSE_DOWNLOAD.equals(purpose)) {
            return purpose;
        }
        throw new BusinessException("附件访问用途不支持");
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
