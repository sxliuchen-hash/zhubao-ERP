package com.zhubao.erp.hardware.app;

import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.hardware.domain.HardwareDevice;
import com.zhubao.erp.hardware.domain.HardwareDeviceStatus;
import com.zhubao.erp.hardware.domain.HardwarePocEvidenceSummary;
import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.domain.HardwarePocRecordRepository;
import com.zhubao.erp.hardware.domain.IntegrationTask;
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

    // M2 models compatibility and fallback, not vendor-specific device SDK integration.
    private final List<HardwareDevice> devices = List.of(
            new HardwareDevice(
                    "CAM-UNPACK-01",
                    "拆包台摄像头",
                    "来料拆包视频证据",
                    "NVR 时间段截取 / RTSP 预留",
                    HardwareDeviceStatus.DEGRADED,
                    "截取失败后允许人工上传 MP4"
            ),
            new HardwareDevice(
                    "PRN-LABEL-01",
                    "标签打印机",
                    "母单/子工单标签",
                    "浏览器打印 / Windows 驱动",
                    HardwareDeviceStatus.MANUAL,
                    "使用 PDF 标签模板人工打印"
            ),
            new HardwareDevice(
                    "SCAN-PC-01",
                    "PC 扫码枪",
                    "后台收货和标签复核",
                    "USB HID 键盘模式",
                    HardwareDeviceStatus.ONLINE,
                    "手工录入编码并二次确认"
            ),
            new HardwareDevice(
                    "PWA-CAM-01",
                    "手机摄像头扫码",
                    "PWA 报工/质检",
                    "BarcodeDetector / ZXing 兜底",
                    HardwareDeviceStatus.DEGRADED,
                    "扫码失败时手工输入工单码"
            ),
            new HardwareDevice(
                    "SCALE-01",
                    "电子秤",
                    "称重交接",
                    "一期人工读数，二期 RS232/USB/TCP",
                    HardwareDeviceStatus.MANUAL,
                    "人工录入并拍照留证"
            )
    );

    private final List<IntegrationTask> tasks = List.of(
            new IntegrationTask(
                    "INT-VID-001",
                    "拆包视频截取任务",
                    "CAM-UNPACK-01 / MD-20260706-004",
                    62,
                    "17:08 截取失败",
                    "重试一次，仍失败则人工上传",
                    "库管员"
            ),
            new IntegrationTask(
                    "INT-LBL-001",
                    "母单标签打印验证",
                    "PRN-LABEL-01 / MD-20260706-001",
                    88,
                    "16:42 打印成功",
                    "补打日志写入审计",
                    "库管员"
            ),
            new IntegrationTask(
                    "INT-PWA-001",
                    "PWA 扫码报工验证",
                    "PWA-CAM-01 / ZD-20260706-001",
                    74,
                    "15:30 识别成功",
                    "补移动端弱光测试",
                    "车间组长"
            ),
            new IntegrationTask(
                    "INT-SCALE-001",
                    "称重交接录入验证",
                    "SCALE-01 / ZD-20260706-006",
                    55,
                    "14:12 人工录入",
                    "确认拍照留证和误差参数",
                    "质检员"
            )
    );

    public List<HardwareDevice> findDevices() {
        return devices;
    }

    public List<IntegrationTask> findTasks() {
        return tasks;
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

    public long fallbackRequiredCount() {
        return devices.stream()
                .filter(device -> device.status() != HardwareDeviceStatus.ONLINE)
                .count();
    }

    public int averageHealth() {
        return (int) Math.round(tasks.stream()
                .mapToInt(IntegrationTask::health)
                .average()
                .orElse(0));
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
