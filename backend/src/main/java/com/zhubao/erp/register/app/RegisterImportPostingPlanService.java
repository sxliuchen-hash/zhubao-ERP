package com.zhubao.erp.register.app;

import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportBatchRow;
import com.zhubao.erp.register.domain.RegisterImportPostingDraft;
import com.zhubao.erp.register.domain.RegisterImportPostingIssue;
import com.zhubao.erp.register.domain.RegisterImportPostingPlan;
import com.zhubao.erp.register.domain.RegisterImportPreviewMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegisterImportPostingPlanService {

    private final RegisterImportConfirmationService confirmationService;

    public RegisterImportPostingPlanService(RegisterImportConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    public RegisterImportPostingPlan plan(String importBatchNo) {
        RegisterImportBatch batch = confirmationService.batch(importBatchNo);
        List<RegisterImportPostingDraft> drafts = batch.rows().stream()
                .map(row -> draft(batch, row))
                .toList();
        int posted = countByStatus(drafts, "POSTED");
        int blocked = countByStatus(drafts, "BLOCKED");
        int warning = countByStatus(drafts, "WARNING");
        int ready = countByStatus(drafts, "READY");
        String status = posted == drafts.size() && !drafts.isEmpty()
                ? "POSTED"
                : blocked > 0 ? "BLOCKED" : warning > 0 ? "WARNING" : "READY";
        return new RegisterImportPostingPlan(
                batch.importBatchNo(),
                batch.templateCode(),
                batch.templateName(),
                postingTargetCommand(batch),
                status,
                batch.totalRows(),
                ready,
                warning,
                blocked,
                nextAction(status, batch.templateCode()),
                drafts
        );
    }

    private RegisterImportPostingDraft draft(RegisterImportBatch batch, RegisterImportBatchRow row) {
        Map<String, String> fields = row.normalizedFields() == null ? Map.of() : row.normalizedFields();
        List<RegisterImportPostingIssue> missing = new ArrayList<>();
        Map<String, String> mapped = switch (batch.templateCode()) {
            case "REG-INBOUND" -> inboundDraft(batch, fields, missing);
            case "REG-OUTBOUND" -> outboundDraft(batch, fields, missing);
            case "REG-POLISHING" -> productionDraft(batch, fields, missing, false);
            case "REG-ENGRAVING" -> productionDraft(batch, fields, missing, true);
            default -> unknownDraft(batch, fields, missing);
        };
        List<RegisterImportPreviewMessage> warnings = warningMessages(row);
        String status = "POSTED".equals(row.status())
                ? "POSTED"
                : !missing.isEmpty() ? "BLOCKED" : warnings.isEmpty() ? "READY" : "WARNING";
        return new RegisterImportPostingDraft(
                batch.importBatchNo(),
                row.sourceLineNo(),
                row.sourceRegisterSequence(),
                postingTargetCommand(batch),
                status,
                mapped,
                List.copyOf(missing),
                warnings
        );
    }

    private Map<String, String> inboundDraft(
            RegisterImportBatch batch,
            Map<String, String> fields,
            List<RegisterImportPostingIssue> missing
    ) {
        Map<String, String> mapped = commonDraftFields(batch, fields);
        put(mapped, "motherOrderCode", fields.get("motherOrderCode"));
        put(mapped, "customerId", fields.get("customerId"));
        put(mapped, "ownerCustomerId", fields.get("ownerCustomerId"));
        put(mapped, "materialCode", fields.get("materialCode"));
        put(mapped, "warehouseCode", valueOrDefault(fields.get("warehouseCode"), "RAW-CUSTOMER"));
        put(mapped, "inboundDate", fields.get("inboundDate"));
        put(mapped, "expressCompany", fields.get("expressCompany"));
        put(mapped, "courierNo", fields.get("courierNo"));
        put(mapped, "senderName", fields.get("senderName"));
        put(mapped, "receiverName", fields.get("receiverName"));
        put(mapped, "unpackSigner", fields.get("unpackSigner"));
        put(mapped, "goodsName", fields.get("goodsName"));
        put(mapped, "quantity", fields.get("quantity"));
        put(mapped, "quantityUnit", valueOrDefault(fields.get("quantityUnit"), "件"));
        put(mapped, "weight", fields.get("weight"));
        put(mapped, "operator", valueOrDefault(fields.get("operator"), batch.confirmedBy()));
        put(mapped, "evidenceCode", fields.get("evidenceCode"));
        put(mapped, "remark", fields.get("remark"));
        put(mapped, "processingRequirement", fields.get("processingRequirement"));
        put(mapped, "dueDate", fields.get("dueDate"));
        put(mapped, "customerName", fields.get("customerName"));
        require(missing, mapped, "motherOrderCode", "缺少系统母单号，不能确认客户来料入库");
        require(missing, mapped, "processingRequirement", "缺少加工要求，不能确认客户来料入库");
        require(missing, mapped, "dueDate", "缺少完成期限，不能确认客户来料入库");
        require(missing, mapped, "customerId", "缺少客户 ID，不能建立客户所有库存维度");
        require(missing, mapped, "materialCode", "缺少物料编码，不能写库存台账");
        require(missing, mapped, "quantity", "缺少入库数量，不能写库存台账");
        require(missing, mapped, "weight", "缺少拆包称重重量，不能写库存台账");
        return mapped;
    }

    private Map<String, String> outboundDraft(
            RegisterImportBatch batch,
            Map<String, String> fields,
            List<RegisterImportPostingIssue> missing
    ) {
        Map<String, String> mapped = commonDraftFields(batch, fields);
        put(mapped, "shipmentCode", fields.get("shipmentCode"));
        put(mapped, "motherOrderCode", fields.get("motherOrderCode"));
        put(mapped, "workOrderCode", fields.get("workOrderCode"));
        put(mapped, "customerId", fields.get("customerId"));
        put(mapped, "customerNameSnapshot", fields.get("customerNameSnapshot"));
        put(mapped, "ownerCustomerId", fields.get("ownerCustomerId"));
        put(mapped, "materialCode", fields.get("materialCode"));
        put(mapped, "fromWarehouseCode", valueOrDefault(fields.get("fromWarehouseCode"), "FINISHED"));
        put(mapped, "toWarehouseCode", valueOrDefault(fields.get("toWarehouseCode"), "SHIPPED"));
        put(mapped, "deliveryDate", fields.get("deliveryDate"));
        put(mapped, "deliveryCategory", fields.get("deliveryCategory"));
        put(mapped, "quantity", fields.get("quantity"));
        put(mapped, "quantityUnit", valueOrDefault(fields.get("quantityUnit"), "件"));
        put(mapped, "weight", fields.get("weight"));
        put(mapped, "expressCompany", fields.get("expressCompany"));
        put(mapped, "logisticsNo", fields.get("logisticsNo"));
        put(mapped, "paperExpressText", fields.get("paperExpressText"));
        put(mapped, "handler", fields.get("handler"));
        put(mapped, "operator", valueOrDefault(fields.get("operator"), batch.confirmedBy()));
        put(mapped, "evidenceCode", fields.get("evidenceCode"));
        put(mapped, "remark", fields.get("remark"));
        require(missing, mapped, "shipmentCode", "缺少系统出库单号，不能生成出库业务单据");
        require(missing, mapped, "motherOrderCode", "缺少母单号，不能追溯客户来料来源");
        require(missing, mapped, "workOrderCode", "缺少子工单号，不能定位成品库存");
        require(missing, mapped, "customerId", "缺少客户 ID，不能保持客户所有库存隔离");
        require(missing, mapped, "materialCode", "缺少物料编码，不能扣减成品库存");
        require(missing, mapped, "quantity", "缺少出货数量，不能扣减成品库存");
        require(missing, mapped, "weight", "缺少出货重量，不能写库存台账");
        require(missing, mapped, "logisticsNo", "缺少物流单号，不能确认发货");
        return mapped;
    }

    private Map<String, String> productionDraft(
            RegisterImportBatch batch,
            Map<String, String> fields,
            List<RegisterImportPostingIssue> missing,
            boolean engraving
    ) {
        Map<String, String> mapped = commonDraftFields(batch, fields);
        put(mapped, "motherOrderCode", fields.get("motherOrderCode"));
        put(mapped, "workOrderCode", fields.get("workOrderCode"));
        put(mapped, "customerId", fields.get("customerId"));
        put(mapped, "ownerCustomerId", fields.get("ownerCustomerId"));
        put(mapped, "materialCode", fields.get("materialCode"));
        put(mapped, "fromWarehouseCode", fields.get("fromWarehouseCode"));
        put(mapped, "toWarehouseCode", fields.get("toWarehouseCode"));
        put(mapped, "quantity", fields.get("quantity"));
        put(mapped, "quantityUnit", valueOrDefault(fields.get("quantityUnit"), "件"));
        put(mapped, "weight", fields.get("weight"));
        put(mapped, "workDate", fields.get("workDate"));
        put(mapped, "process", engraving ? valueOrDefault(fields.get("process"), "雕刻") : valueOrDefault(fields.get("process"), fields.get("workItemName")));
        put(mapped, "processCategory", engraving ? "设备工序" : "人工工序");
        put(mapped, "productCategory", fields.get("productCategory"));
        put(mapped, "machineLineNo", fields.get("machineLineNo"));
        put(mapped, "machineId", fields.get("machineId"));
        put(mapped, "machineNo", fields.get("machineNo"));
        put(mapped, "workItemName", fields.get("workItemName"));
        put(mapped, "startedAt", fields.get("startedAt"));
        put(mapped, "finishedAt", fields.get("finishedAt"));
        put(mapped, "completionStatus", fields.get("completionStatus"));
        put(mapped, "handoffAuditStatus", fields.get("handoffAuditStatus"));
        put(mapped, "operator", valueOrDefault(fields.get("operator"), batch.confirmedBy()));
        put(mapped, "operatorEmployeeId", fields.get("operatorEmployeeId"));
        put(mapped, "operatorSignatureName", fields.get("operatorSignatureName"));
        put(mapped, "signatureMediaId", fields.get("signatureMediaId"));
        put(mapped, "handoffCode", fields.get("handoffCode"));
        put(mapped, "remark", fields.get("remark"));
        require(missing, mapped, "motherOrderCode", "缺少母单号，不能建立生产报工追溯");
        require(missing, mapped, "workOrderCode", "缺少子工单号，不能定位在制库存");
        require(missing, mapped, "customerId", "缺少客户 ID，不能保持客户所有库存隔离");
        require(missing, mapped, "materialCode", "缺少物料编码，不能写库存台账");
        require(missing, mapped, "fromWarehouseCode", "缺少来源仓库，不能从在制库存转出");
        require(missing, mapped, "toWarehouseCode", "缺少目标仓库，不能生成报工入账");
        require(missing, mapped, "quantity", "缺少报工数量，不能写库存台账");
        require(missing, mapped, "weight", "缺少报工重量，不能写库存台账");
        require(missing, mapped, "process", "缺少工序名称，不能生成报工记录");
        if (engraving) {
            require(missing, mapped, "workDate", "缺少设备产量日期，不能生成雕刻统计记录");
            require(missing, mapped, "productCategory", "缺少品类，不能生成雕刻统计记录");
            require(missing, mapped, "machineNo", "缺少机器编号，不能生成设备产量记录");
            require(missing, mapped, "operatorSignatureName", "缺少操机签字，不能确认设备产量");
        }
        return mapped;
    }

    private Map<String, String> unknownDraft(
            RegisterImportBatch batch,
            Map<String, String> fields,
            List<RegisterImportPostingIssue> missing
    ) {
        Map<String, String> mapped = commonDraftFields(batch, fields);
        missing.add(new RegisterImportPostingIssue(
                "templateCode",
                "BLOCK",
                "未知登记表模板，不能生成入账映射计划",
                "补充模板到入账映射服务"
        ));
        return mapped;
    }

    private Map<String, String> commonDraftFields(RegisterImportBatch batch, Map<String, String> fields) {
        Map<String, String> mapped = new LinkedHashMap<>();
        put(mapped, "importBatchNo", batch.importBatchNo());
        put(mapped, "sourceSheetName", fields.get("sourceSheetName"));
        put(mapped, "sourceRegisterType", fields.get("sourceRegisterType"));
        put(mapped, "sourceRegisterLineNo", fields.get("sourceRegisterLineNo"));
        put(mapped, "sourceRegisterSequence", fields.get("sourceRegisterSequence"));
        return mapped;
    }

    private String postingTargetCommand(RegisterImportBatch batch) {
        if ("REG-ENGRAVING".equals(batch.templateCode())) {
            return "MachineOutputRecordDraft / ReportProductionCommand";
        }
        return batch.targetCommand();
    }

    private List<RegisterImportPreviewMessage> warningMessages(RegisterImportBatchRow row) {
        if (row.messages() == null) {
            return List.of();
        }
        return row.messages().stream()
                .filter(message -> "WARN".equals(message.level()))
                .toList();
    }

    private int countByStatus(List<RegisterImportPostingDraft> drafts, String status) {
        return (int) drafts.stream().filter(draft -> status.equals(draft.status())).count();
    }

    private String nextAction(String status, String templateCode) {
        if ("POSTED".equals(status)) {
            return "该登记表批次已完成显式入账，重复执行会被拒绝";
        }
        if ("BLOCKED".equals(status)) {
            return "先补齐业务主数据、母单/工单、物料、仓库和称重字段，再允许生成业务单据";
        }
        return switch (templateCode) {
            case "REG-INBOUND" -> "可生成母单/签收入库命令，入账时必须继续通过库存台账服务";
            case "REG-OUTBOUND" -> "可生成出库单和成品出库命令，入账时必须校验客户成品库存";
            case "REG-POLISHING" -> "可生成报工/交单审核命令，入账时必须校验工单和在制库存";
            case "REG-ENGRAVING" -> "可生成设备产量记录草稿，绑定工单后再进入报工和工资试算";
            default -> "待补充入账映射规则";
        };
    }

    private void put(Map<String, String> fields, String key, String value) {
        fields.put(key, value == null ? "" : value.trim());
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private void require(List<RegisterImportPostingIssue> missing, Map<String, String> mapped, String field, String message) {
        String value = mapped.get(field);
        if (value == null || value.isBlank()) {
            missing.add(new RegisterImportPostingIssue(field, "BLOCK", message, "在导入复核页补齐或匹配主数据后重试"));
        }
    }
}
