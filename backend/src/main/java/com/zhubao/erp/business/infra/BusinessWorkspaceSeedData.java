package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessReviewData.ActionItem;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundOrder;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryBalance;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryLedgerEntry;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.LossApproval;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileTask;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionHandoff;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityCase;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ReviewStat;
import com.zhubao.erp.business.domain.BusinessReviewData.TraceEvent;
import com.zhubao.erp.business.domain.BusinessReviewData.WorkOrder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessWorkspaceSeedData {

    // M2 review seed data. M3 should replace this provider with database seed records.
    public InboundWorkspaceData inbound() {
        return new InboundWorkspaceData(
                List.of(
                        new ReviewStat("待扫码收货", "2 单", "快递单未绑定母单", "amber"),
                        new ReviewStat("待拆包称重", "3 单", "需记录拆包前后重量", "blue"),
                        new ReviewStat("视频完整率", "92%", "1 个截取任务失败", "amber"),
                        new ReviewStat("标签可打印", "4 张", "含 1 张待补打", "green")
                ),
                List.of(
                        new InboundOrder("MD-20260706-001", "恒信珠宝", "18K 金戒托", "SF1048329001", 320, "486.25 g", "待拆包", "待开始", "客户所有"),
                        new InboundOrder("MD-20260706-002", "雅辰饰品", "银链半成品", "YT7740192207", 1200, "2,840.00 g", "已入库", "已关联", "客户所有"),
                        new InboundOrder("MD-20260706-003", "璟瑞工贸", "宝石托坯", "JD9301827446", 540, "718.30 g", "待确认物料", "截取中", "客户所有"),
                        new InboundOrder("MD-20260706-004", "瑞禾珠宝", "PT950 吊坠坯", "DB7730018291", 180, "392.10 g", "异常待处理", "失败", "客户所有")
                ),
                List.of(
                        new ActionItem("MD-20260706-003", "物料名称待确认", "宝石托盘客户描述与历史物料档案不一致，需要业务员确认后才能入账。", "业务员", "确认物料档案", "amber"),
                        new ActionItem("MD-20260706-004", "拆包视频截取失败", "NVR 时间段任务返回失败，系统允许重试一次，仍失败则人工上传视频。", "库管员", "重试截取", "red")
                )
        );
    }

    public ProductionWorkspaceData production() {
        return new ProductionWorkspaceData(
                List.of(
                        new ReviewStat("抛光", "6 张 / 78%", "2 张等待称重交接", "amber"),
                        new ReviewStat("镶石", "5 张 / 92%", "质检积压，暂缓派新单", "red"),
                        new ReviewStat("焊接", "4 张 / 64%", "设备正常，可继续派工", "green"),
                        new ReviewStat("终检", "3 张 / 55%", "等待缺陷照片归档", "blue")
                ),
                List.of(
                        new WorkOrder("ZD-20260706-001", "恒信珠宝", "18K 金戒托", "抛光", "李华", 80, 56, "生产中", 70),
                        new WorkOrder("ZD-20260706-002", "恒信珠宝", "18K 金戒托", "镶石", "陈敏", 80, 80, "待质检", 100),
                        new WorkOrder("ZD-20260706-006", "雅辰饰品", "银链半成品", "焊接", "王强", 300, 210, "称重待补", 70),
                        new WorkOrder("FG-20260706-002", "瑞禾珠宝", "PT950 吊坠坯", "返工抛光", "赵磊", 24, 12, "返工中", 50)
                ),
                List.of(
                        new ProductionHandoff("HO-20260706-014", "粗抛 / 李华", "精抛 / 陈敏", "121.15 g", "119.92 g", "1.02%", "可交接", "green"),
                        new ProductionHandoff("HO-20260706-018", "焊接 / 王强", "质检 / 周娜", "684.40 g", "683.20 g", "0.18%", "待补照片", "amber"),
                        new ProductionHandoff("HO-20260706-021", "抛光 / 赵磊", "返工复检 / 刘洋", "52.40 g", "50.41 g", "3.80%", "审批阻断", "red")
                )
        );
    }

    public QualityWorkspaceData quality() {
        return new QualityWorkspaceData(
                List.of(
                        new ReviewStat("待派检", "7 批", "最早提交 09:40", "amber"),
                        new ReviewStat("返工复检", "2 批", "均已生成返工工单", "violet"),
                        new ReviewStat("缺陷照片", "15 张", "2 张待补拍", "blue"),
                        new ReviewStat("损耗阻断", "1 条", "超过工序标准 1.8%", "red")
                ),
                List.of(
                        new QualityCase("QC-20260706-021", "ZD-20260706-002", "镶石", "周娜", "松石 3 件，划痕 2 件", "返工", "待生成返工工单"),
                        new QualityCase("QC-20260706-022", "ZD-20260706-006", "焊接", "周娜", "焊点偏移 6 件", "复检", "待确认称重"),
                        new QualityCase("QC-20260706-023", "FG-20260706-001", "返工抛光", "刘洋", "返工通过", "放行", "已完成")
                ),
                List.of(
                        new LossApproval("LOSS-20260706-009", "ZD-20260706-001", "抛光", "2.0%", "3.8%", "投入 121.15g / 产出 116.55g / 缺陷照片 5 张", "待组长审批", "red"),
                        new LossApproval("LOSS-20260706-010", "ZD-20260706-006", "焊接", "0.8%", "待确认", "缺少产出称重照片", "待补称重", "amber")
                )
        );
    }

    public InventoryWorkspaceData inventory() {
        return new InventoryWorkspaceData(
                List.of(
                        new InventoryBalance("恒信珠宝", "MD-20260706-001", "18K 金戒托", "原料库 A-01", "待加工", 240, "365.10 g", "0"),
                        new InventoryBalance("恒信珠宝", "ZD-20260706-001", "18K 金戒托", "车间-抛光", "在制", 80, "121.15 g", "0"),
                        new InventoryBalance("雅辰饰品", "MD-20260706-002", "银链半成品", "成品库 F-02", "成品", 620, "1,451.20 g", "200 件"),
                        new InventoryBalance("瑞禾珠宝", "FG-20260706-002", "PT950 吊坠坯", "返工区 R-01", "返工在制", 24, "52.40 g", "0")
                ),
                List.of(
                        new InventoryLedgerEntry("09:05", "LEDGER-20260706-001", "来料入库", "0 件 / 0 g", "320 件 / 486.25 g", "MD-20260706-001", "何琳"),
                        new InventoryLedgerEntry("10:20", "LEDGER-20260706-007", "工单领料", "320 件 / 486.25 g", "240 件 / 365.10 g", "ZD-20260706-001", "马杰"),
                        new InventoryLedgerEntry("14:30", "LEDGER-20260706-018", "工序产出", "80 件 / 121.15 g", "56 件 / 84.95 g", "BG-20260706-044", "李华"),
                        new InventoryLedgerEntry("15:10", "LEDGER-20260706-022", "质检冻结", "56 件 / 84.95 g", "5 件冻结 / 7.20 g", "QC-20260706-021", "周娜")
                ),
                List.of(
                        new TraceEvent("08:42", "扫码收货", "SF1048329001", "库管员 何琳", "快递单照片"),
                        new TraceEvent("09:05", "拆包称重", "MD-20260706-001", "库管员 何琳", "拆包视频 05:14"),
                        new TraceEvent("10:20", "生成子工单", "ZD-20260706-001", "车间组长 马杰", "母单标签扫描"),
                        new TraceEvent("14:30", "工序报工", "BG-20260706-044", "操作工 李华", "称重交接 121.15g"),
                        new TraceEvent("15:10", "质检异常", "QC-20260706-021", "质检员 周娜", "缺陷照片 5 张")
                ),
                List.of(
                        new ActionItem("EV-BIZ-001", "业务单据", "母单 / 子工单 / 报工 / 质检 / 台账 / 出库", "系统", "已关联 6 张", "green"),
                        new ActionItem("EV-WEIGHT-001", "称重记录", "HO-20260706-018 / HO-20260706-021", "车间组长", "2 条待补照片", "amber"),
                        new ActionItem("EV-VIDEO-001", "视频证据", "INT-VID-001", "库管员", "1 个截取失败", "red"),
                        new ActionItem("EV-AUDIT-001", "操作审计", "扫码、补打、审批均已留痕", "系统", "完整", "green")
                )
        );
    }

    public MobileWorkspaceData mobile() {
        return new MobileWorkspaceData(
                List.of(
                        new MobileTask("ZD-20260706-001", "扫码进入报工", "提交抛光数量 24 件", "数量、重量、照片可选", "可操作"),
                        new MobileTask("ZD-20260706-006", "称重交接", "补录投入/产出重量", "投入重量、产出重量、交接人", "必填阻断"),
                        new MobileTask("QC-20260706-021", "移动质检", "选择返工原因并拍照", "不合格数量、原因、处理方式", "待质检")
                ),
                List.of(
                        new ReviewStat("扫码识别", "可用", "BarcodeDetector 优先，ZXing 兜底，失败可手输编码。", "green"),
                        new ReviewStat("离线草稿", "设计中", "弱网时暂存报工和质检，恢复网络后补提交。", "amber"),
                        new ReviewStat("拍照留证", "一期必做", "缺陷、称重、人工视频兜底都需要附件。", "blue"),
                        new ReviewStat("权限边界", "待接入", "当前为样例身份，M3 接入真实角色权限。", "gray")
                ),
                List.of(
                        new ActionItem("DRAFT-BG-001", "抛光报工", "恢复网络后需复核工单状态", "李华", "14:28", "amber"),
                        new ActionItem("DRAFT-QC-003", "缺陷拍照", "照片已缓存，质检结果未提交", "周娜", "15:06", "red")
                )
        );
    }
}
