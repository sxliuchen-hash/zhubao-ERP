package com.zhubao.erp.hardware.app;

import com.zhubao.erp.hardware.domain.HardwareDevice;
import com.zhubao.erp.hardware.domain.HardwareDeviceStatus;
import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.domain.IntegrationTask;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HardwareIntegrationService {

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

    private final List<HardwarePocRecord> pocRecords = List.of(
            new HardwarePocRecord(
                    "POC-VID-20260706-001",
                    "CAM-UNPACK-01",
                    "拆包台视频截取",
                    "2026-07-06 08:40-09:10",
                    "有条件通过",
                    "NVR 时间段可定位，FFmpeg 截取偶发超时",
                    "需要确认 NVR 账号权限和时间同步",
                    "实施顾问",
                    "补充 NTP 校时检查和一次弱网重试"
            ),
            new HardwarePocRecord(
                    "POC-LBL-20260706-001",
                    "PRN-LABEL-01",
                    "母单/子工单标签打印",
                    "2026-07-06 10:30-10:45",
                    "通过",
                    "浏览器打印成功，PDF 模板可兜底",
                    "无阻断",
                    "库管员",
                    "锁定标签模板尺寸并补打印审计"
            ),
            new HardwarePocRecord(
                    "POC-SCAN-20260706-001",
                    "PWA-CAM-01",
                    "PWA 弱光扫码",
                    "2026-07-06 15:20-15:45",
                    "阻断",
                    "弱光下识别率不足，手工输入可继续业务",
                    "需要补充 ZXing 兜底和现场光照标准",
                    "车间组长",
                    "安排第二轮弱光 POC 并记录失败样本"
            ),
            new HardwarePocRecord(
                    "POC-SCALE-20260706-001",
                    "SCALE-01",
                    "称重交接人工录入",
                    "2026-07-06 14:00-14:20",
                    "有条件通过",
                    "人工读数、拍照留证、误差参数可支撑一期",
                    "二期电子秤接口未冻结",
                    "质检员",
                    "一期冻结人工录入，二期单独验证 RS232/USB/TCP"
            )
    );

    public List<HardwareDevice> findDevices() {
        return devices;
    }

    public List<IntegrationTask> findTasks() {
        return tasks;
    }

    public List<HardwarePocRecord> findPocRecords() {
        return pocRecords;
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
        return pocRecords.stream()
                .filter(record -> record.result().contains("阻断"))
                .count();
    }
}
