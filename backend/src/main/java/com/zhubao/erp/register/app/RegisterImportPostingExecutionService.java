package com.zhubao.erp.register.app;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.OutboundShipmentResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.machine.app.MachineOutputService;
import com.zhubao.erp.machine.domain.MachineOutputRecord;
import com.zhubao.erp.machine.domain.SubmitMachineOutputCommand;
import com.zhubao.erp.register.domain.PostRegisterImportBatchCommand;
import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportPostingDraft;
import com.zhubao.erp.register.domain.RegisterImportPostingExecutionItem;
import com.zhubao.erp.register.domain.RegisterImportPostingExecutionResult;
import com.zhubao.erp.register.domain.RegisterImportPostingPlan;
import com.zhubao.erp.security.domain.AuthUserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RegisterImportPostingExecutionService {

    private static final DateTimeFormatter POST_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RegisterImportConfirmationService confirmationService;
    private final RegisterImportPostingPlanService postingPlanService;
    private final BusinessOperationService businessOperationService;
    private final MachineOutputService machineOutputService;
    private final ProcessStationService processStationService;

    public RegisterImportPostingExecutionService(
            RegisterImportConfirmationService confirmationService,
            RegisterImportPostingPlanService postingPlanService,
            BusinessOperationService businessOperationService,
            MachineOutputService machineOutputService
    ) {
        this(confirmationService, postingPlanService, businessOperationService, machineOutputService, null);
    }

    @Autowired
    public RegisterImportPostingExecutionService(
            RegisterImportConfirmationService confirmationService,
            RegisterImportPostingPlanService postingPlanService,
            BusinessOperationService businessOperationService,
            MachineOutputService machineOutputService,
            ProcessStationService processStationService
    ) {
        this.confirmationService = confirmationService;
        this.postingPlanService = postingPlanService;
        this.businessOperationService = businessOperationService;
        this.machineOutputService = machineOutputService;
        this.processStationService = processStationService;
    }

    public RegisterImportPostingExecutionResult post(String importBatchNo, PostRegisterImportBatchCommand command) {
        return postInternal(importBatchNo, command, null);
    }

    public RegisterImportPostingExecutionResult post(String importBatchNo, PostRegisterImportBatchCommand command, AuthUserAccount account) {
        if (account == null) throw new BusinessException("登记表入账必须登录");
        return postInternal(importBatchNo, command, account);
    }

    private RegisterImportPostingExecutionResult postInternal(String importBatchNo, PostRegisterImportBatchCommand command, AuthUserAccount account) {
        RegisterImportBatch batch = confirmationService.batch(importBatchNo);
        if ("POSTED".equals(batch.status())) {
            throw new BusinessException("导入批次已入账，不能重复执行：" + importBatchNo);
        }

        RegisterImportPostingPlan plan = postingPlanService.plan(importBatchNo);
        if (plan.blockedDrafts() > 0) {
            throw new BusinessException("入账映射计划存在阻断行，不能执行入账：" + importBatchNo);
        }
        if (plan.warningDrafts() > 0 && !allowWarnings(command)) {
            throw new BusinessException("入账映射计划存在警告行，请显式 allowWarnings 后再执行：" + importBatchNo);
        }

        String operator = account == null ? normalizeOperator(command, batch) : account.operator();
        String postedAt = POST_TIME_FORMATTER.format(LocalDateTime.now());
        List<PreparedPosting> preparedPostings = plan.drafts().stream()
                .filter(draft -> !"POSTED".equals(draft.status()))
                .map(draft -> prepare(plan.templateCode(), draft, operator, command))
                .toList();
        if (preparedPostings.isEmpty()) {
            throw new BusinessException("导入批次没有可执行的入账草稿：" + importBatchNo);
        }

        List<RegisterImportPostingExecutionItem> items = new ArrayList<>();
        for (PreparedPosting posting : preparedPostings) {
            RegisterImportPostingExecutionItem item = execute(plan.templateCode(), posting, account);
            items.add(item);
            confirmationService.markRowsPosted(
                    importBatchNo,
                    List.of(posting.draft().sourceLineNo()),
                    false,
                    "部分登记表行已执行显式入账，继续完成剩余行"
            );
        }

        List<Integer> postedLines = preparedPostings.stream()
                .map(posting -> posting.draft().sourceLineNo())
                .toList();
        confirmationService.markRowsPosted(
                importBatchNo,
                postedLines,
                true,
                "已执行显式业务入账命令，重复执行将被拒绝"
        );

        return new RegisterImportPostingExecutionResult(
                plan.importBatchNo(),
                plan.templateCode(),
                plan.templateName(),
                "POSTED",
                plan.totalRows(),
                items.size(),
                plan.totalRows() - items.size(),
                plan.blockedDrafts(),
                plan.warningDrafts(),
                operator,
                postedAt,
                "已通过后端业务服务完成入账；库存变化已进入库存台账",
                List.copyOf(items)
        );
    }

    private PreparedPosting prepare(
            String templateCode,
            RegisterImportPostingDraft draft,
            String operator,
            PostRegisterImportBatchCommand command
    ) {
        Map<String, String> fields = draft.mappedFields();
        Object businessCommand = switch (templateCode) {
            case "REG-INBOUND" -> inboundCommand(draft, fields, operator, command);
            case "REG-OUTBOUND" -> outboundCommand(draft, fields, operator, command);
            case "REG-POLISHING", "REG-ENGRAVING" -> productionCommand(draft, fields, operator, command);
            default -> throw new BusinessException("暂不支持该登记表模板入账：" + templateCode);
        };
        return new PreparedPosting(draft, businessCommand, command);
    }

    private RegisterImportPostingExecutionItem execute(String templateCode, PreparedPosting posting, AuthUserAccount account) {
        Object command = posting.businessCommand();
        if (command instanceof ConfirmInboundCommand inboundCommand) {
            InboundConfirmationResult result = businessOperationService.confirmInbound(inboundCommand);
            return item(posting.draft(), result.motherOrderCode(), result.ledgerCode(), "已执行 ConfirmInboundCommand");
        }
        if (command instanceof ShipFinishedStockCommand outboundCommand) {
            OutboundShipmentResult result = businessOperationService.shipFinishedStock(outboundCommand, outboundCommand.operator());
            return item(posting.draft(), result.shipmentCode(), result.ledgerCode(), "已执行 ShipFinishedStockCommand");
        }
        if (command instanceof ReportProductionCommand productionCommand) {
            if (account == null || processStationService == null) {
                throw new BusinessException("登记表报工必须登录并经过岗位校验，不能绕过工序");
            }
            ProductionReportResult result = processStationService.reportProduction(productionCommand, account);
            String message = "已执行 ReportProductionCommand";
            if ("REG-ENGRAVING".equals(templateCode)) {
                MachineOutputRecord outputRecord = submitMachineOutput(posting.draft(), result, posting.command());
                message = "已执行 ReportProductionCommand；已生成设备产量记录 " + outputRecord.code();
            }
            return item(posting.draft(), result.workOrderCode(), result.ledgerCode(), message);
        }
        throw new BusinessException("未知入账命令类型：" + command.getClass().getSimpleName());
    }

    private RegisterImportPostingExecutionItem item(
            RegisterImportPostingDraft draft,
            String businessCode,
            String ledgerCode,
            String message
    ) {
        return new RegisterImportPostingExecutionItem(
                draft.sourceLineNo(),
                draft.sourceRegisterSequence(),
                draft.targetCommand(),
                "POSTED",
                businessCode,
                ledgerCode,
                message
        );
    }

    private MachineOutputRecord submitMachineOutput(
            RegisterImportPostingDraft draft,
            ProductionReportResult result,
            PostRegisterImportBatchCommand command
    ) {
        Map<String, String> fields = draft.mappedFields();
        return machineOutputService.submit(new SubmitMachineOutputCommand(
                machineOutputCode(draft),
                value(fields, "workDate"),
                longValue(fields, "customerId"),
                value(fields, "customerNameSnapshot"),
                value(fields, "productCategory"),
                optionalLong(fields, "machineId"),
                value(fields, "machineNo"),
                value(fields, "workOrderCode"),
                optionalLong(fields, "workProcessId"),
                result.workOrderCode(),
                decimalValue(fields, "quantity"),
                value(fields, "quantityUnit"),
                optionalLong(fields, "operatorEmployeeId"),
                value(fields, "operatorSignatureName"),
                optionalLong(fields, "signatureMediaId"),
                value(fields, "sourceRegisterType"),
                sourceLineNo(draft, fields),
                value(fields, "machineLineNo"),
                value(fields, "sourceRegisterSequence"),
                draft.importBatchNo(),
                result.ledgerCode(),
                "SUBMITTED",
                postingRemark(draft, fields, command),
                result.operator()
        ));
    }

    private ConfirmInboundCommand inboundCommand(
            RegisterImportPostingDraft draft,
            Map<String, String> fields,
            String operator,
            PostRegisterImportBatchCommand command
    ) {
        return new ConfirmInboundCommand(
                value(fields, "motherOrderCode"),
                longValue(fields, "customerId"),
                optionalLong(fields, "ownerCustomerId"),
                value(fields, "materialCode"),
                value(fields, "inboundDate"),
                value(fields, "expressCompany"),
                value(fields, "courierNo"),
                value(fields, "senderName"),
                value(fields, "receiverName"),
                value(fields, "unpackSigner"),
                value(fields, "goodsName"),
                value(fields, "sourceRegisterType"),
                sourceLineNo(draft, fields),
                value(fields, "sourceRegisterSequence"),
                value(fields, "warehouseCode"),
                decimalValue(fields, "quantity"),
                value(fields, "quantityUnit"),
                decimalValue(fields, "weight"),
                operator,
                value(fields, "evidenceCode"),
                postingRemark(draft, fields, command),
                value(fields, "processingRequirement"),
                value(fields, "dueDate"),
                value(fields, "customerName")
        );
    }

    private ShipFinishedStockCommand outboundCommand(
            RegisterImportPostingDraft draft,
            Map<String, String> fields,
            String operator,
            PostRegisterImportBatchCommand command
    ) {
        return new ShipFinishedStockCommand(
                value(fields, "shipmentCode"),
                value(fields, "motherOrderCode"),
                value(fields, "workOrderCode"),
                longValue(fields, "customerId"),
                optionalLong(fields, "ownerCustomerId"),
                value(fields, "materialCode"),
                value(fields, "fromWarehouseCode"),
                value(fields, "toWarehouseCode"),
                value(fields, "deliveryDate"),
                value(fields, "deliveryCategory"),
                decimalValue(fields, "quantity"),
                value(fields, "quantityUnit"),
                decimalValue(fields, "weight"),
                value(fields, "expressCompany"),
                value(fields, "logisticsNo"),
                value(fields, "paperExpressText"),
                value(fields, "sourceRegisterType"),
                sourceLineNo(draft, fields),
                value(fields, "sourceRegisterSequence"),
                value(fields, "handler"),
                operator,
                value(fields, "evidenceCode"),
                postingRemark(draft, fields, command),
                "register-outbound:" + value(fields, "shipmentCode") + ":" + sourceLineNo(draft, fields)
        );
    }

    private ReportProductionCommand productionCommand(
            RegisterImportPostingDraft draft,
            Map<String, String> fields,
            String operator,
            PostRegisterImportBatchCommand command
    ) {
        return new ReportProductionCommand(
                value(fields, "motherOrderCode"),
                value(fields, "workOrderCode"),
                longValue(fields, "customerId"),
                optionalLong(fields, "ownerCustomerId"),
                value(fields, "materialCode"),
                value(fields, "fromWarehouseCode"),
                value(fields, "toWarehouseCode"),
                decimalValue(fields, "quantity"),
                value(fields, "quantityUnit"),
                decimalValue(fields, "weight"),
                value(fields, "workDate"),
                value(fields, "process"),
                value(fields, "processCategory"),
                value(fields, "productCategory"),
                value(fields, "machineNo"),
                value(fields, "workItemName"),
                value(fields, "startedAt"),
                value(fields, "finishedAt"),
                value(fields, "completionStatus"),
                value(fields, "handoffAuditStatus"),
                value(fields, "sourceRegisterType"),
                sourceLineNo(draft, fields),
                value(fields, "sourceRegisterSequence"),
                operator,
                value(fields, "operatorSignatureName"),
                value(fields, "handoffCode"),
                postingRemark(draft, fields, command)
        );
    }

    private boolean allowWarnings(PostRegisterImportBatchCommand command) {
        return command != null && Boolean.TRUE.equals(command.allowWarnings());
    }

    private String normalizeOperator(PostRegisterImportBatchCommand command, RegisterImportBatch batch) {
        if (command != null && command.operator() != null && !command.operator().isBlank()) {
            return command.operator().trim();
        }
        return batch.confirmedBy();
    }

    private String postingRemark(
            RegisterImportPostingDraft draft,
            Map<String, String> fields,
            PostRegisterImportBatchCommand command
    ) {
        String remark = value(fields, "remark");
        String userRemark = command == null ? "" : blankToEmpty(command.postingRemark());
        String auditRemark = "登记表批次入账：" + draft.importBatchNo() + " / 原行：" + draft.sourceLineNo();
        return joinRemark(remark, userRemark, auditRemark);
    }

    private String joinRemark(String... remarks) {
        List<String> values = new ArrayList<>();
        for (String remark : remarks) {
            if (remark != null && !remark.isBlank()) {
                values.add(remark.trim());
            }
        }
        return String.join("；", values);
    }

    private String value(Map<String, String> fields, String field) {
        return blankToEmpty(fields.get(field));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Long longValue(Map<String, String> fields, String field) {
        String value = value(fields, field);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(field + " 必须是整数：" + value);
        }
    }

    private Long optionalLong(Map<String, String> fields, String field) {
        String value = value(fields, field);
        if (value.isBlank()) {
            return null;
        }
        return longValue(fields, field);
    }

    private BigDecimal decimalValue(Map<String, String> fields, String field) {
        String value = value(fields, field);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(field + " 必须是数字：" + value);
        }
    }

    private Integer sourceLineNo(RegisterImportPostingDraft draft, Map<String, String> fields) {
        String value = value(fields, "sourceRegisterLineNo");
        if (value.isBlank()) {
            return draft.sourceLineNo();
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException("sourceRegisterLineNo 必须是整数：" + value);
        }
    }

    private String machineOutputCode(RegisterImportPostingDraft draft) {
        String compactBatchNo = draft.importBatchNo().replaceAll("[^A-Za-z0-9]", "");
        String code = "MOR-" + compactBatchNo + "-" + draft.sourceLineNo();
        if (code.length() <= 60) {
            return code;
        }
        return code.substring(0, 55) + "-" + draft.sourceLineNo();
    }

    private record PreparedPosting(
            RegisterImportPostingDraft draft,
            Object businessCommand,
            PostRegisterImportBatchCommand command
    ) {
    }
}
