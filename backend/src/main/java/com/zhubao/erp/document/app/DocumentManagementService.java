package com.zhubao.erp.document.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.AttachmentContent;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.domain.AttachmentPreviewUrl;
import com.zhubao.erp.document.domain.DocumentAuditEvent;
import com.zhubao.erp.document.domain.DocumentManagementSummary;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachmentUploadResult;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.StoredAttachmentObject;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DocumentManagementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AttachmentObjectStorage attachmentObjectStorage;
    private final AtomicInteger uploadSequence = new AtomicInteger(1);
    private final Map<String, String> uploadedStorageKeys = new ConcurrentHashMap<>();

    public DocumentManagementService(AttachmentObjectStorage attachmentObjectStorage) {
        this.attachmentObjectStorage = attachmentObjectStorage;
    }

    private final List<DocumentPackage> packages = new ArrayList<>(List.of(
            new DocumentPackage(
                    "DOC-INBOUND-20260706-001",
                    "来料母单证据包",
                    "业务单据",
                    "MD-20260706-001",
                    "库管员 何琳",
                    "归档快递单、拆包称重、标签和入库台账，支撑客户来料追溯。",
                    5,
                    4,
                    "缺称重照片",
                    "业务附件保留 3 年，视频片段保留 180 天",
                    "2026-07-06 15:30",
                    "amber"
            ),
            new DocumentPackage(
                    "DOC-QC-20260706-021",
                    "质检返工证据包",
                    "质检证据",
                    "QC-20260706-021",
                    "质检员 周娜",
                    "归档缺陷照片、返工原因、返工派发和返工报工记录。",
                    6,
                    6,
                    "已归档",
                    "质检附件保留 3 年",
                    "2026-07-06 16:05",
                    "green"
            ),
            new DocumentPackage(
                    "DOC-HW-POC-20260706-001",
                    "硬件 POC 证据包",
                    "软硬件联动",
                    "POC-SCAN-20260706-001",
                    "实施负责人",
                    "归档 NVR、标签打印、PWA 扫码和电子秤验证证据。",
                    4,
                    3,
                    "待补失败日志",
                    "POC 记录保留到项目验收后 1 年",
                    "2026-07-06 17:20",
                    "amber"
            ),
            new DocumentPackage(
                    "DOC-CONFIG-20260706-001",
                    "配置变更评审包",
                    "配置/构型",
                    "CFG-CR-20260706-001",
                    "生产负责人",
                    "归档配置变更、审批意见、影响范围和回滚说明。",
                    4,
                    2,
                    "待审批补全",
                    "配置审计长期保留",
                    "2026-07-06 16:40",
                    "red"
            )
    ));

    private final List<EvidenceAttachment> attachments = new ArrayList<>(List.of(
            new EvidenceAttachment(
                    "ATT-IN-20260706-001",
                    "DOC-INBOUND-20260706-001",
                    "来料母单",
                    "MD-20260706-001",
                    "SF1048329001-waybill.jpg",
                    "image/jpeg",
                    "s3://zhubao-evidence/inbound/20260706/SF1048329001-waybill.jpg",
                    "hot 180d -> archive",
                    "PWA 拍照上传",
                    "已关联",
                    "库管员 何琳",
                    "sha256:inbound-waybill-demo",
                    "2026-07-06 08:45",
                    "无",
                    "green"
            ),
            new EvidenceAttachment(
                    "ATT-IN-20260706-002",
                    "DOC-INBOUND-20260706-001",
                    "称重交接",
                    "HO-20260706-014",
                    "HO-20260706-014-weight.jpg",
                    "image/jpeg",
                    "待上传",
                    "hot 180d -> archive",
                    "人工补传",
                    "缺失",
                    "库管员 何琳",
                    "待生成",
                    "待上传",
                    "PWA 或 PC 后台补传称重照片",
                    "red"
            ),
            new EvidenceAttachment(
                    "ATT-QC-20260706-021",
                    "DOC-QC-20260706-021",
                    "质检记录",
                    "QC-20260706-021",
                    "QC-20260706-021-defects.zip",
                    "application/zip",
                    "s3://zhubao-evidence/quality/20260706/QC-20260706-021-defects.zip",
                    "hot 365d -> archive",
                    "质检台上传",
                    "已归档",
                    "质检员 周娜",
                    "sha256:qc-defect-demo",
                    "2026-07-06 15:12",
                    "无",
                    "green"
            ),
            new EvidenceAttachment(
                    "ATT-HW-20260706-001",
                    "DOC-HW-POC-20260706-001",
                    "硬件 POC",
                    "POC-SCAN-20260706-001",
                    "pwa-low-light-scan.mp4",
                    "video/mp4",
                    "nas://evidence/poc/20260706/pwa-low-light-scan.mp4",
                    "hot 90d -> archive",
                    "现场录屏",
                    "有条件通过",
                    "实施负责人",
                    "sha256:poc-video-demo",
                    "2026-07-06 17:08",
                    "弱光失败时允许手工录入编码",
                    "amber"
            ),
            new EvidenceAttachment(
                    "ATT-CFG-20260706-001",
                    "DOC-CONFIG-20260706-001",
                    "配置变更",
                    "CFG-CR-20260706-001",
                    "loss-rate-change-review.pdf",
                    "application/pdf",
                    "待审批后归档",
                    "audit long-term",
                    "配置评审",
                    "待审批",
                    "生产负责人",
                    "待生成",
                    "待归档",
                    "审批完成后锁定版本",
                    "amber"
            )
    ));

    private final List<DocumentAuditEvent> auditEvents = new ArrayList<>(List.of(
            new DocumentAuditEvent("2026-07-06 08:45", "DOC-AUD-001", "DOC-INBOUND-20260706-001", "上传附件", "库管员 何琳", "上传快递单照片并绑定母单。", "green"),
            new DocumentAuditEvent("2026-07-06 15:12", "DOC-AUD-002", "DOC-QC-20260706-021", "归档质检证据", "质检员 周娜", "上传缺陷照片压缩包并关联返工工单。", "green"),
            new DocumentAuditEvent("2026-07-06 17:08", "DOC-AUD-003", "DOC-HW-POC-20260706-001", "登记 POC 证据", "实施负责人", "关联 PWA 弱光扫码录屏和兜底说明。", "amber"),
            new DocumentAuditEvent("2026-07-06 17:20", "DOC-AUD-004", "DOC-CONFIG-20260706-001", "创建评审包", "生产负责人", "配置变更单等待审批，附件暂不锁定。", "red")
    ));

    public DocumentManagementSummary summary() {
        long missingEvidence = attachments.stream()
                .filter(attachment -> !attachment.status().equals("已关联")
                        && !attachment.status().equals("已归档")
                        && !attachment.status().equals("已上传"))
                .count();
        long manualFallbacks = attachments.stream()
                .filter(attachment -> !attachment.fallbackAction().equals("无"))
                .count();
        return new DocumentManagementSummary(
                packages.size(),
                attachments.size(),
                (int) missingEvidence,
                (int) manualFallbacks,
                auditEvents.size()
        );
    }

    public List<DocumentPackage> packages() {
        return packages;
    }

    public List<EvidenceAttachment> attachments() {
        return attachments;
    }

    public List<DocumentAuditEvent> auditEvents() {
        return auditEvents;
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
        if (packages.stream().noneMatch(documentPackage -> documentPackage.code().equals(documentCode))) {
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
        attachments.add(attachment);
        uploadedStorageKeys.put(code, storedObject.storageKey());
        auditEvents.add(new DocumentAuditEvent(
                now,
                "DOC-AUD-UP-" + String.format("%03d", auditEvents.size() + 1),
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
            throw new BusinessException("附件尚未进入本地对象存储：" + code);
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
        return attachments.stream()
                .filter(attachment -> attachment.code().equals(code))
                .findFirst();
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
