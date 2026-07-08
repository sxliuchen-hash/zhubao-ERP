package com.zhubao.erp.hardware.infra;

import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.domain.HardwarePocRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.hardware.poc.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryHardwarePocRecordRepository implements HardwarePocRecordRepository {

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

    @Override
    public List<HardwarePocRecord> findAll() {
        return pocRecords;
    }
}
