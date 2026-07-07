package com.zhubao.erp.register.app;

import com.zhubao.erp.register.domain.RegisterFieldMapping;
import com.zhubao.erp.register.domain.RegisterImportTemplate;
import com.zhubao.erp.register.domain.RegisterValidationRule;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegisterImportTemplateService {

    private final List<RegisterImportTemplate> templates = List.of(
            inboundTemplate(),
            outboundTemplate(),
            polishingTemplate(),
            engravingTemplate()
    );

    public List<RegisterImportTemplate> findTemplates() {
        return templates;
    }

    public RegisterImportSummary summary() {
        int fieldCount = templates.stream().mapToInt(template -> template.fields().size()).sum();
        long requiredFields = templates.stream()
                .flatMap(template -> template.fields().stream())
                .filter(RegisterFieldMapping::required)
                .count();
        long readyTemplates = templates.stream()
                .filter(template -> "COMMAND_READY".equals(template.parserStatus()))
                .count();
        return new RegisterImportSummary(
                templates.size(),
                fieldCount,
                requiredFields,
                readyTemplates,
                "Excel preview parser ready; confirm import pending"
        );
    }

    private RegisterImportTemplate inboundTemplate() {
        return new RegisterImportTemplate(
                "REG-INBOUND",
                "签收入库登记表",
                "入库登记表.xlsx",
                "收货签收 -> 拆包称重 -> 客户所有原料入账",
                "ConfirmInboundCommand",
                "inbound_confirm",
                "COMMAND_READY",
                "补 sourceSheetName/importBatchNo 后接入 Excel 上传解析",
                List.of(
                        field("序号", "sourceRegisterSequence", "ConfirmInboundCommand", false, "保留纸质序号", "不作业务主键"),
                        field("工作表名", "sourceSheetName", "RegisterImportPreview", false, "取 Excel 工作表名称", "用于定位原始工作表"),
                        field("入库日期", "inboundDate", "ConfirmInboundCommand", true, "月日补全年份", "yyyy-MM-dd"),
                        field("快递名称", "expressCompany", "ConfirmInboundCommand", true, "原文 trim", "不能为空"),
                        field("快递单号", "courierNo", "ConfirmInboundCommand", true, "原文 trim", "全局追溯入口，建议唯一"),
                        field("寄货人姓名", "senderName", "ConfirmInboundCommand", false, "姓名快照", "可为空但应提示补全"),
                        field("收货人姓名", "receiverName", "ConfirmInboundCommand", false, "姓名快照", "可为空但应提示补全"),
                        field("拆箱签字", "unpackSigner", "ConfirmInboundCommand", true, "姓名快照", "一期保存姓名，二期接签名附件"),
                        field("货品名称", "goodsName", "ConfirmInboundCommand", true, "现场名称快照", "必须映射物料档案"),
                        field("数量", "quantity + quantityUnit", "ConfirmInboundCommand", true, "数量默认单位件", "数量必须大于 0")
                ),
                List.of(
                        rule("IN-001", "BLOCK", "快递单号重复时不得确认入库", "转人工复核"),
                        rule("IN-002", "WARN", "纸质月日必须按业务日期补全年份", "导入预览标黄"),
                        rule("IN-003", "BLOCK", "未确认客户和物料前不得拆分工单", "保存草稿")
                )
        );
    }

    private RegisterImportTemplate outboundTemplate() {
        return new RegisterImportTemplate(
                "REG-OUTBOUND",
                "交货情况登记表",
                "出货登记表.xlsx",
                "成品库存 -> 打包出货 -> 已发货库存",
                "ShipFinishedStockCommand",
                "outbound_shipment",
                "COMMAND_READY",
                "补出库单结构化持久化和导入批次表",
                List.of(
                        field("序号", "sourceRegisterSequence", "ShipFinishedStockCommand", false, "保留纸质序号", "不作业务主键"),
                        field("工作表名", "sourceSheetName", "RegisterImportPreview", false, "取 Excel 工作表名称", "用于定位原始工作表"),
                        field("日期", "deliveryDate", "ShipFinishedStockCommand", true, "月日补全年份", "yyyy-MM-dd"),
                        field("客户", "customerId/customerNameSnapshot", "ShipFinishedStockCommand", true, "客户名称匹配主数据", "匹配失败转待确认"),
                        field("交货类目", "deliveryCategory", "ShipFinishedStockCommand", true, "枚举映射", "成品/返修件/补发件/样品"),
                        field("交货数量", "quantity + quantityUnit", "ShipFinishedStockCommand", true, "数量默认单位件", "必须匹配库存单位"),
                        field("快递", "paperExpressText + expressCompany + logisticsNo", "ShipFinishedStockCommand", true, "原文保留后拆分", "物流单号不能为空"),
                        field("经办人", "handler", "ShipFinishedStockCommand", true, "姓名快照", "与系统操作人分开保存"),
                        field("备注", "remark", "ShipFinishedStockCommand", false, "原文保留", "不得承载关键字段")
                ),
                List.of(
                        rule("OUT-001", "BLOCK", "出库数量不得超过客户成品可用库存", "拒绝确认出货"),
                        rule("OUT-002", "BLOCK", "经办人缺失不得确认发货", "保存草稿"),
                        rule("OUT-003", "WARN", "纸质快递栏原文必须保留", "写入 paperExpressText")
                )
        );
    }

    private RegisterImportTemplate polishingTemplate() {
        return new RegisterImportTemplate(
                "REG-POLISHING",
                "工作项目记录单（日）",
                "抛光登记表.xlsx",
                "工序日报 -> 交单审核 -> 工序报工",
                "ReportProductionCommand",
                "production_report",
                "COMMAND_READY",
                "补日报导入预览和交单审核动作",
                List.of(
                        field("姓名", "operator", "ReportProductionCommand", true, "表头继承到明细行", "必须匹配员工或保存姓名快照"),
                        field("日期", "workDate", "ReportProductionCommand", true, "表头继承到明细行", "yyyy-MM-dd"),
                        field("序号", "sourceRegisterSequence", "ReportProductionCommand", false, "保留纸质序号", "不作业务主键"),
                        field("工作表名", "sourceSheetName", "RegisterImportPreview", false, "取 Excel 工作表名称", "用于定位原始工作表"),
                        field("工作项目", "workItemName", "ReportProductionCommand", true, "原文 trim", "粗抛/精抛/返工抛光等"),
                        field("开始时间", "startedAt", "ReportProductionCommand", false, "与 workDate 组合", "跨班次需人工确认"),
                        field("完成时间", "finishedAt", "ReportProductionCommand", false, "与 workDate 组合", "进行中可为空"),
                        field("完成情况", "completionStatus", "ReportProductionCommand", true, "原文快照", "不等同工序状态"),
                        field("交单审核", "handoffAuditStatus", "ReportProductionCommand", true, "审核状态快照", "未审核不得进下一工序"),
                        field("备注", "remark", "ReportProductionCommand", false, "原文保留", "异常说明")
                ),
                List.of(
                        rule("POL-001", "BLOCK", "姓名和日期必须展开到每条工作项目明细", "导入预览阻断"),
                        rule("POL-002", "BLOCK", "未交单审核不得进入下一工序或复检", "保存报工草稿"),
                        rule("POL-003", "WARN", "完成情况不能直接驱动后端状态机", "需工序状态规则复核")
                )
        );
    }

    private RegisterImportTemplate engravingTemplate() {
        return new RegisterImportTemplate(
                "REG-ENGRAVING",
                "雕刻机统计单（日）",
                "雕刻登记表.xlsx",
                "设备产量统计 -> 工序报工/工资试算",
                "ReportProductionCommand / biz_machine_output_record",
                "production_report",
                "COMMAND_READY",
                "新增设备产量独立落库和机器档案匹配",
                List.of(
                        field("序号", "sourceRegisterSequence", "ReportProductionCommand", false, "日内分组序号", "不作业务主键"),
                        field("工作表名", "sourceSheetName", "RegisterImportPreview", false, "取 Excel 工作表名称", "用于定位原始工作表"),
                        field("日期", "workDate", "ReportProductionCommand", true, "合并单元格继承", "yyyy-MM-dd"),
                        field("客户", "customerId/customerNameSnapshot", "ReportProductionCommand", true, "合并单元格继承并匹配主数据", "匹配失败转待确认"),
                        field("品类", "productCategory", "ReportProductionCommand", true, "合并单元格继承", "产能统计必填"),
                        field("机器行号", "machineLineNo", "RegisterImportPreview", false, "保留纸质 1-8 台机器行", "用于回表定位"),
                        field("机器编号", "machineNo", "ReportProductionCommand", true, "1-8 号机器行保留", "未匹配设备档案时保存文本"),
                        field("雕刻数量", "quantity + quantityUnit", "ReportProductionCommand", true, "默认单位件", "数量必须大于 0"),
                        field("操机签字", "operatorSignatureName", "ReportProductionCommand", true, "姓名快照", "不能用录入人替代"),
                        field("备注", "remark", "ReportProductionCommand", false, "原文保留", "设备异常说明")
                ),
                List.of(
                        rule("ENG-001", "BLOCK", "日期、客户、品类空白格必须从上一组合并区域继承", "导入预览阻断"),
                        rule("ENG-002", "BLOCK", "操机签字缺失不得确认设备产量", "保存草稿"),
                        rule("ENG-003", "WARN", "机器编号未匹配设备档案时标记待匹配", "允许先保存文本")
                )
        );
    }

    private RegisterFieldMapping field(
            String sourceColumn,
            String targetField,
            String targetObject,
            boolean required,
            String normalizeRule,
            String validationRule
    ) {
        return new RegisterFieldMapping(sourceColumn, targetField, targetObject, required, normalizeRule, validationRule);
    }

    private RegisterValidationRule rule(String code, String level, String description, String fallback) {
        return new RegisterValidationRule(code, level, description, fallback);
    }

    public record RegisterImportSummary(
            int templates,
            int fields,
            long requiredFields,
            long commandReadyTemplates,
            String parserStatus
    ) {
    }
}
