package com.zhubao.erp.hardware.app;

import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.hardware.domain.HardwarePocEvidenceSummary;
import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.domain.HardwarePocRecordRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class HardwareIntegrationService {

    private final HardwarePocRecordRepository pocRecordRepository;
    private final DocumentManagementRepository documentManagementRepository;

    public HardwareIntegrationService(
            HardwarePocRecordRepository pocRecordRepository,
            DocumentManagementRepository documentManagementRepository
    ) {
        this.pocRecordRepository = pocRecordRepository;
        this.documentManagementRepository = documentManagementRepository;
    }

    public List<HardwarePocRecord> findPocRecords() {
        return pocRecordRepository.findAll();
    }

    public List<HardwarePocEvidenceSummary> findPocEvidence() {
        List<EvidenceAttachment> hardwareAttachments = documentManagementRepository.attachments().stream()
                .filter(attachment -> "硬件 POC".equals(attachment.businessType()))
                .toList();
        return findPocRecords().stream()
                .map(record -> summarizeEvidence(record, hardwareAttachments))
                .toList();
    }

    public long blockedPocCount() {
        return findPocRecords().stream()
                .filter(record -> record.result().contains("阻断"))
                .count();
    }

    private HardwarePocEvidenceSummary summarizeEvidence(
            HardwarePocRecord record,
            List<EvidenceAttachment> hardwareAttachments
    ) {
        List<EvidenceAttachment> matchedAttachments = hardwareAttachments.stream()
                .filter(attachment -> record.code().equals(attachment.businessCode()))
                .toList();
        if (matchedAttachments.isEmpty()) {
            return new HardwarePocEvidenceSummary(
                    record.code(),
                    record.scene(),
                    record.result(),
                    "",
                    0,
                    1,
                    List.of(),
                    "",
                    "待补证据",
                    "补传现场 POC 原始照片、视频或日志",
                    "red"
            );
        }

        int missingEvidence = (int) matchedAttachments.stream()
                .filter(this::missingEvidence)
                .count();
        EvidenceAttachment latest = matchedAttachments.stream()
                .max(Comparator.comparing(EvidenceAttachment::uploadedAt))
                .orElse(matchedAttachments.get(0));
        String nextAction = matchedAttachments.stream()
                .map(EvidenceAttachment::fallbackAction)
                .filter(action -> action != null && !action.isBlank() && !"无".equals(action))
                .findFirst()
                .orElse("继续保留 POC 证据并等待现场复测");

        return new HardwarePocEvidenceSummary(
                record.code(),
                record.scene(),
                record.result(),
                latest.documentCode(),
                matchedAttachments.size(),
                missingEvidence,
                matchedAttachments.stream().map(EvidenceAttachment::code).toList(),
                latest.uploadedAt(),
                missingEvidence > 0 ? "证据待补" : "已关联证据",
                nextAction,
                missingEvidence > 0 ? "amber" : latest.tone()
        );
    }

    private boolean missingEvidence(EvidenceAttachment attachment) {
        return attachment.status().contains("缺失")
                || attachment.status().contains("待上传")
                || attachment.storageLocation().contains("待上传")
                || attachment.checksum().contains("待生成");
    }
}
