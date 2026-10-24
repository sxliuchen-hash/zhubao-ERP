package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.business.domain.AfterSalesReviewCommand;
import com.zhubao.erp.business.domain.AfterSalesReviewDisposition;
import com.zhubao.erp.business.domain.AfterSalesReviewResult;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.CustomerReturnCommand;
import com.zhubao.erp.business.domain.CustomerReturnResult;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundShipmentResult;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.QualityFreezeResult;
import com.zhubao.erp.business.domain.QualityReleaseResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ReportReworkCommand;
import com.zhubao.erp.business.domain.ReleaseQualityStockCommand;
import com.zhubao.erp.business.domain.ReworkDispatchResult;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.label.app.LabelVersionGuard;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class BusinessOperationService {

    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StockLedgerService stockLedgerService;
    private final ApprovalService approvalService;
    private final BusinessDocumentRepository businessDocumentRepository;
    private final BusinessWorkUnit workUnit;
    private final MotherOrderSaveProbe motherOrderSaveProbe;
    private final OutboundSaveProbe outboundSaveProbe;
    private LabelVersionGuard labelGuard;
    private MasterDataService masterData;

    public BusinessOperationService(StockLedgerService stockLedgerService) {
        this(stockLedgerService, null, null);
    }

    public BusinessOperationService(StockLedgerService stockLedgerService, ApprovalService approvalService) {
        this(stockLedgerService, approvalService, null);
    }

    public BusinessOperationService(
            StockLedgerService stockLedgerService,
            ApprovalService approvalService,
            BusinessDocumentRepository businessDocumentRepository
    ) {
        this(stockLedgerService, approvalService, businessDocumentRepository, null, MotherOrderSaveProbe.NONE, OutboundSaveProbe.NONE);
    }

    public BusinessOperationService(
            StockLedgerService stockLedgerService,
            ApprovalService approvalService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit
    ) {
        this(stockLedgerService, approvalService, businessDocumentRepository, workUnit, MotherOrderSaveProbe.NONE, OutboundSaveProbe.NONE);
    }

    @Autowired
    public BusinessOperationService(StockLedgerService stockLedgerService, ApprovalService approvalService,
            BusinessDocumentRepository businessDocumentRepository, BusinessWorkUnit workUnit, MasterDataService masterData) {
        this(stockLedgerService, approvalService, businessDocumentRepository, workUnit);
        this.masterData = masterData;
    }

    public BusinessOperationService(
            StockLedgerService stockLedgerService,
            ApprovalService approvalService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit,
            MotherOrderSaveProbe motherOrderSaveProbe
    ) {
        this(stockLedgerService, approvalService, businessDocumentRepository, workUnit, motherOrderSaveProbe, OutboundSaveProbe.NONE);
    }

    public BusinessOperationService(
            StockLedgerService stockLedgerService,
            ApprovalService approvalService,
            BusinessDocumentRepository businessDocumentRepository,
            BusinessWorkUnit workUnit,
            MotherOrderSaveProbe motherOrderSaveProbe,
            OutboundSaveProbe outboundSaveProbe
    ) {
        this.stockLedgerService = stockLedgerService;
        this.approvalService = approvalService;
        this.businessDocumentRepository = businessDocumentRepository;
        this.workUnit = workUnit;
        this.motherOrderSaveProbe = motherOrderSaveProbe == null ? MotherOrderSaveProbe.NONE : motherOrderSaveProbe;
        this.outboundSaveProbe = outboundSaveProbe == null ? OutboundSaveProbe.NONE : outboundSaveProbe;
    }

    @Autowired(required = false)
    public void setLabelVersionGuard(LabelVersionGuard labelGuard) {
        this.labelGuard = labelGuard;
    }

    public InboundConfirmationResult confirmInbound(ConfirmInboundCommand command) {
        return postInbound(command, null);
    }

    /**
     * 带请求编号的入库确认。母单、来料事实、入库流水和成功结果同一次提交。
     * 未装配工作单元时拒绝，不能退回各自提交的旧路径。
     */
    public InboundConfirmationResult confirmInbound(ConfirmInboundCommand command, String actorId) {
        if (command.requestId() == null || command.requestId().isBlank()) {
            return confirmInbound(command);
        }
        if (workUnit == null) {
            throw new BusinessException("入库回放尚未装配");
        }
        if (businessDocumentRepository == null) {
            throw new BusinessException("来料单据仓储尚未装配");
        }
        String text = workUnit.execute(new CommandCall(
                command.requestId(),
                actorId,
                "INBOUND_CONFIRM",
                "MOTHER_ORDER",
                command.motherOrderCode(),
                ReceivingResultText.inboundPayload(command)
        ), session -> ReceivingResultText.inbound(postInbound(command, session)));
        return ReceivingResultText.inbound(text);
    }

    private InboundConfirmationResult postInbound(ConfirmInboundCommand command, WorkSession session) {
        assertRequiredText(command.processingRequirement(), "加工要求不能为空");
        assertDueDate(command.dueDate());
        assertMotherOrderAvailable(command.motherOrderCode(), session);
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension rawCustomerStock = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                null,
                command.materialCode(),
                command.warehouseCode(),
                StockStatus.RAW_WAITING
        );

        StockTransferCommand transfer = new StockTransferCommand(
                "inbound_confirm",
                command.motherOrderCode(),
                null,
                rawCustomerStock,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        );
        StockLedgerEntry entry = session == null
                ? stockLedgerService.transfer(transfer)
                : stockLedgerService.transfer(transfer, session);
        if (session != null) {
            motherOrderSaveProbe.beforeMotherSave();
        }

        InboundConfirmationResult result = new InboundConfirmationResult(
                command.motherOrderCode(),
                entry.code(),
                command.warehouseCode(),
                rawCustomerStock.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.operator(),
                command.evidenceCode()
        );
        saveMotherOrder(command, rawCustomerStock, entry, session);
        if (session != null && masterData != null && !blankToEmpty(command.sourceRegisterType()).startsWith("INTAKE_LINES:")) {
            var material = masterData.materialForIntake(command.materialCode());
            businessDocumentRepository.saveReceiptLine(command.motherOrderCode(), new com.zhubao.erp.business.domain.ReceiptLine(1,
                    command.materialCode(), material.name(), command.goodsName(), command.quantity(), command.quantityUnit(), command.weight(), entry.code(),
                    com.zhubao.erp.business.domain.StorageRequirements.of(material)), session);
        }
        return result;
    }

    /**
     * 备料转入加工母单时只登记母单。库存已由领用流水转入待加工，这里不再写收货入库。
     */
    public void saveProcessingMotherFromPrep(ConfirmInboundCommand command, StockLedgerEntry movement, WorkSession session) {
        assertRequiredText(command.processingRequirement(), "加工要求不能为空");
        assertDueDate(command.dueDate());
        assertMotherOrderAvailable(command.motherOrderCode(), session);
        if (movement == null || movement.to() == null || !"prep_issue".equals(movement.documentType())) {
            throw new BusinessException("备料建单必须先完成领用，不能再次收货入库");
        }
        if (!command.motherOrderCode().equals(movement.to().motherOrderCode())) {
            throw new BusinessException("领用去向与加工母单不一致");
        }
        saveMotherOrder(command, movement.to(), movement, session);
    }

    /** 供来料建单在已打开的工作单元里入库，不再套一层请求回放。 */
    InboundConfirmationResult postInboundInSession(ConfirmInboundCommand command, WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("入库写入必须处于业务工作单元");
        }
        return postInbound(command, session);
    }

    public ProductionReportResult reportProduction(ReportProductionCommand command) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromWip = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.WIP
        );
        StockDimension toSemiFinished = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                StockStatus.SEMI_FINISHED
        );

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "production_report",
                command.workOrderCode(),
                fromWip,
                toSemiFinished,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

        return new ProductionReportResult(
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromWip.stockStatus(),
                toSemiFinished.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.process(),
                command.operator(),
                command.handoffCode()
        );
    }

    public QualityFreezeResult freezeQualityStock(FreezeQualityStockCommand command) {
        return freezeQualityStock(command, null);
    }

    /** 带工作单元时，冻结流水不另开连接。 */
    public QualityFreezeResult freezeQualityStock(FreezeQualityStockCommand command, WorkSession session) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromSemiFinished = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.SEMI_FINISHED
        );
        StockDimension toFrozen = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                StockStatus.FROZEN
        );

        StockLedgerEntry entry = postTransfer(new StockTransferCommand(
                "quality_freeze",
                command.qualityCaseCode(),
                fromSemiFinished,
                toFrozen,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ), session);

        return new QualityFreezeResult(
                command.qualityCaseCode(),
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromSemiFinished.stockStatus(),
                toFrozen.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.process(),
                command.inspector(),
                command.evidenceCode()
        );
    }

    public ReworkDispatchResult dispatchRework(DispatchReworkCommand command) {
        return dispatchRework(command, null);
    }

    /** 带工作单元时，开返工流水不另开连接。 */
    public ReworkDispatchResult dispatchRework(DispatchReworkCommand command, WorkSession session) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromFrozen = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.FROZEN
        );
        StockDimension toRework = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                StockStatus.REWORK
        );

        StockLedgerEntry entry = postTransfer(new StockTransferCommand(
                "rework_dispatch",
                command.reworkOrderCode(),
                fromFrozen,
                toRework,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ), session);

        return new ReworkDispatchResult(
                command.reworkOrderCode(),
                command.qualityCaseCode(),
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromFrozen.stockStatus(),
                toRework.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.process(),
                command.operator(),
                command.evidenceCode()
        );
    }

    public ReworkReportResult reportRework(ReportReworkCommand command) {
        return reportRework(command, null);
    }

    /** 带工作单元时，返工报工流水不另开连接。 */
    public ReworkReportResult reportRework(ReportReworkCommand command, WorkSession session) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromRework = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.REWORK
        );
        StockDimension toSemiFinished = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                StockStatus.SEMI_FINISHED
        );

        StockLedgerEntry entry = postTransfer(new StockTransferCommand(
                "rework_report",
                command.reworkOrderCode(),
                fromRework,
                toSemiFinished,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ), session);

        return new ReworkReportResult(
                command.reworkOrderCode(),
                command.qualityCaseCode(),
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromRework.stockStatus(),
                toSemiFinished.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.process(),
                command.operator(),
                command.evidenceCode()
        );
    }

    public QualityReleaseResult releaseQualityStock(ReleaseQualityStockCommand command) {
        return releaseQualityStock(command, null);
    }

    /** 带工作单元时，放行流水不另开连接。 */
    public QualityReleaseResult releaseQualityStock(ReleaseQualityStockCommand command, WorkSession session) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromSemiFinished = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.SEMI_FINISHED
        );
        StockDimension toFinished = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                StockStatus.FINISHED
        );

        StockLedgerEntry entry = postTransfer(new StockTransferCommand(
                "quality_release",
                command.qualityCaseCode(),
                fromSemiFinished,
                toFinished,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ), session);

        return new QualityReleaseResult(
                command.qualityCaseCode(),
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromSemiFinished.stockStatus(),
                toFinished.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.process(),
                command.inspector(),
                command.releaseResult(),
                command.evidenceCode()
        );
    }

    public OutboundShipmentResult shipFinishedStock(ShipFinishedStockCommand command) {
        assertShipmentLabel(command, command == null ? "" : command.operator());
        return postShipment(command, null);
    }

    private void assertShipmentLabel(ShipFinishedStockCommand command, String operator) {
        if (labelGuard == null || command == null) {
            return;
        }
        String raw = command.remark() != null && command.remark().startsWith("JG|") ? command.remark() : command.workOrderCode();
        if (raw == null || !raw.startsWith("JG|")) {
            return;
        }
        labelGuard.check(raw, command.motherOrderCode(), command.workOrderCode(), "", new com.zhubao.erp.security.domain.AuthUserAccount(
                operator, "", operator, com.zhubao.erp.security.domain.RoleCode.WAREHOUSE_KEEPER,
                com.zhubao.erp.security.domain.DataScope.ALL, true, false
        ));
    }

    /**
     * 带请求编号的确认发货。库存流水、出货单和成功结果同一次提交。
     * 同账号、同请求、同规范化内容回放原结果。未带编号时仍各自提交，不回放。
     * 未装配工作单元时，带编号的调用直接拒绝，不能退回旧路径。
     */
    public OutboundShipmentResult shipFinishedStock(ShipFinishedStockCommand command, String actorId) {
        assertShipmentLabel(command, actorId);
        if (command.requestId() == null || command.requestId().isBlank()) {
            return shipFinishedStock(command);
        }
        if (workUnit == null) {
            throw new BusinessException("发货回放尚未装配");
        }
        if (businessDocumentRepository == null) {
            throw new BusinessException("出库单据仓储尚未装配");
        }
        String actor = actorId == null ? "" : actorId.trim();
        String text = workUnit.execute(new CommandCall(
                command.requestId(),
                actor,
                "OUTBOUND_SHIPMENT",
                "OUTBOUND_ORDER",
                command.shipmentCode(),
                ShipmentResultText.payload(command)
        ), session -> ShipmentResultText.write(postShipment(command, session)));
        return ShipmentResultText.read(text);
    }

    private OutboundShipmentResult postShipment(ShipFinishedStockCommand command, WorkSession session) {
        assertOutboundOrderAvailable(command.shipmentCode(), session);
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromFinished = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.FINISHED
        );
        StockDimension toShipped = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                StockStatus.SHIPPED
        );

        StockLedgerEntry entry = postTransfer(new StockTransferCommand(
                "outbound_shipment",
                command.shipmentCode(),
                fromFinished,
                toShipped,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ), session);

        OutboundShipmentResult result = new OutboundShipmentResult(
                command.shipmentCode(),
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromFinished.stockStatus(),
                toShipped.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.logisticsNo(),
                command.operator(),
                command.evidenceCode()
        );
        if (businessDocumentRepository != null) {
            outboundSaveProbe.beforeOutboundSave();
            saveOutboundOrder(command, entry, session);
        }
        return result;
    }

    public CustomerReturnResult returnCustomerShipment(CustomerReturnCommand command) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromShipped = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.SHIPPED
        );
        StockDimension toFinished = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                StockStatus.FINISHED
        );

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "customer_return",
                command.returnCode(),
                fromShipped,
                toFinished,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

        return new CustomerReturnResult(
                command.returnCode(),
                command.originalShipmentCode(),
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromShipped.stockStatus(),
                toFinished.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.returnLogisticsNo(),
                command.reason(),
                command.operator(),
                command.evidenceCode()
        );
    }

    public AfterSalesReviewResult reviewAfterSalesReturn(AfterSalesReviewCommand command) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        StockDimension fromReturnCheck = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.fromWarehouseCode(),
                StockStatus.FINISHED
        );
        StockStatus targetStatus = targetStatus(command.disposition());
        StockDimension toReviewedStock = new StockDimension(
                OwnerType.CUSTOMER,
                ownerCustomerId,
                command.customerId(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.materialCode(),
                command.toWarehouseCode(),
                targetStatus
        );

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "after_sales_review",
                command.reviewCode(),
                fromReturnCheck,
                toReviewedStock,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

        return new AfterSalesReviewResult(
                command.reviewCode(),
                command.returnCode(),
                command.originalShipmentCode(),
                command.workOrderCode(),
                entry.code(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                fromReturnCheck.stockStatus(),
                toReviewedStock.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.disposition(),
                command.inspector(),
                command.finding(),
                command.evidenceCode()
        );
    }

    public LossApprovalResult approveLoss(ApproveLossCommand command) {
        BigDecimal rawLossWeight = command.inputWeight()
                .subtract(command.outputWeight())
                .subtract(command.reasonableTransferWeight());
        BigDecimal lossWeight = rawLossWeight.max(ZERO).setScale(3, RoundingMode.HALF_UP);
        BigDecimal allowedLossWeight = command.inputWeight()
                .multiply(command.standardLossRatePercent())
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP)
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal excessLossWeight = lossWeight.subtract(allowedLossWeight).max(ZERO).setScale(3, RoundingMode.HALF_UP);
        BigDecimal actualLossRatePercent = lossWeight
                .multiply(ONE_HUNDRED)
                .divide(command.inputWeight(), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pieceWage = command.acceptedQuantity()
                .multiply(command.pieceRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal theoreticalDeduction = excessLossWeight
                .multiply(command.lossDeductionUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal lossDeduction = theoreticalDeduction.min(pieceWage).setScale(2, RoundingMode.HALF_UP);
        BigDecimal trialWage = pieceWage.subtract(lossDeduction).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean overLimit = excessLossWeight.compareTo(ZERO) > 0;

        LossApprovalResult result = new LossApprovalResult(
                command.approvalCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.process(),
                command.inputWeight().setScale(3, RoundingMode.HALF_UP),
                command.outputWeight().setScale(3, RoundingMode.HALF_UP),
                command.reasonableTransferWeight().setScale(3, RoundingMode.HALF_UP),
                lossWeight,
                allowedLossWeight,
                excessLossWeight,
                command.standardLossRatePercent().setScale(2, RoundingMode.HALF_UP),
                actualLossRatePercent,
                overLimit,
                overLimit ? "WAITING_APPROVAL" : "AUTO_APPROVED",
                command.acceptedQuantity().setScale(0, RoundingMode.HALF_UP),
                command.pieceRate().setScale(2, RoundingMode.HALF_UP),
                pieceWage,
                lossDeduction,
                trialWage,
                false,
                command.operator(),
                command.approver(),
                command.evidenceCode()
        );
        if (approvalService != null) {
            approvalService.createLossApprovalTask(command, result);
        }
        return result;
    }

    private String buildRemark(ConfirmInboundCommand command) {
        StringBuilder remark = new StringBuilder("确认来料入库；操作人：").append(command.operator());
        appendField(remark, "入库日期", command.inboundDate());
        appendField(remark, "快递", command.expressCompany());
        appendField(remark, "快递单号", command.courierNo());
        appendField(remark, "寄货人", command.senderName());
        appendField(remark, "收货人", command.receiverName());
        appendField(remark, "拆箱签字", command.unpackSigner());
        appendField(remark, "货品名称", command.goodsName());
        appendField(remark, "客户", command.customerName());
        appendField(remark, "加工要求", command.processingRequirement());
        appendField(remark, "完成期限", command.dueDate());
        appendField(remark, "数量单位", command.quantityUnit());
        appendRegisterSource(remark, command.sourceRegisterType(), command.sourceRegisterLineNo(), command.sourceRegisterSequence());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private StockLedgerEntry postTransfer(StockTransferCommand command, WorkSession session) {
        if (session == null) {
            return stockLedgerService.transfer(command);
        }
        return stockLedgerService.transfer(command, session);
    }

    private void assertMotherOrderAvailable(String code, WorkSession session) {
        if (businessDocumentRepository == null) {
            return;
        }
        boolean exists = session == null
                ? businessDocumentRepository.motherOrderExists(code)
                : businessDocumentRepository.motherOrderExists(code, session);
        if (exists) {
            throw new BusinessException("结构化母单已存在，不能重复确认入库：" + code);
        }
    }

    private void assertOutboundOrderAvailable(String code, WorkSession session) {
        if (businessDocumentRepository == null) {
            return;
        }
        boolean exists = session == null
                ? businessDocumentRepository.outboundOrderExists(code)
                : businessDocumentRepository.outboundOrderExists(code, session);
        if (exists) {
            throw new BusinessException("结构化出库单已存在，不能重复确认出库：" + code);
        }
    }

    private void saveMotherOrder(
            ConfirmInboundCommand command,
            StockDimension stockDimension,
            StockLedgerEntry entry,
            WorkSession session
    ) {
        if (businessDocumentRepository == null) {
            return;
        }
        MotherOrderRecord record = new MotherOrderRecord(
                command.motherOrderCode(),
                command.customerId(),
                stockDimension.ownerCustomerId(),
                command.materialCode(),
                blankToEmpty(command.inboundDate()),
                blankToEmpty(command.expressCompany()),
                blankToEmpty(command.courierNo()),
                blankToEmpty(command.senderName()),
                blankToEmpty(command.receiverName()),
                blankToEmpty(command.unpackSigner()),
                blankToEmpty(command.goodsName()),
                stockDimension.ownerType().name(),
                stockDimension.ownerType() == OwnerType.CUSTOMER,
                command.warehouseCode(),
                entry.quantity(),
                blankToDefault(command.quantityUnit(), "件"),
                entry.weight(),
                stockDimension.stockStatus().name(),
                entry.code(),
                blankToEmpty(command.sourceRegisterType()),
                command.sourceRegisterLineNo(),
                blankToEmpty(command.sourceRegisterSequence()),
                command.operator(),
                blankToEmpty(command.evidenceCode()),
                blankToEmpty(command.remark()),
                "INBOUND_CONFIRMED",
                RECORD_TIME.format(LocalDateTime.now()),
                command.processingRequirement().trim(),
                command.dueDate().trim(),
                blankToEmpty(command.customerName())
        );
        if (session == null) {
            businessDocumentRepository.saveMotherOrder(record);
        } else {
            businessDocumentRepository.saveMotherOrder(record, session);
        }
    }

    private void assertRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
    }

    private void assertDueDate(String dueDate) {
        assertRequiredText(dueDate, "完成期限不能为空");
        try {
            LocalDate.parse(dueDate.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("完成期限需使用 yyyy-MM-dd");
        }
    }

    private void saveOutboundOrder(ShipFinishedStockCommand command, StockLedgerEntry entry, WorkSession session) {
        OutboundOrderRecord record = new OutboundOrderRecord(
                command.shipmentCode(),
                command.motherOrderCode(),
                command.workOrderCode(),
                command.customerId(),
                command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId(),
                command.materialCode(),
                command.fromWarehouseCode(),
                command.toWarehouseCode(),
                blankToEmpty(command.deliveryDate()),
                blankToEmpty(command.deliveryCategory()),
                entry.quantity(),
                blankToDefault(command.quantityUnit(), "件"),
                entry.weight(),
                blankToEmpty(command.expressCompany()),
                command.logisticsNo(),
                blankToEmpty(command.paperExpressText()),
                blankToEmpty(command.handler()),
                blankToEmpty(command.sourceRegisterType()),
                command.sourceRegisterLineNo(),
                blankToEmpty(command.sourceRegisterSequence()),
                command.operator(),
                blankToEmpty(command.evidenceCode()),
                blankToEmpty(command.remark()),
                "SHIPPED",
                entry.code(),
                RECORD_TIME.format(LocalDateTime.now())
        );
        if (session == null) {
            businessDocumentRepository.saveOutboundOrder(record);
        } else {
            businessDocumentRepository.saveOutboundOrder(record, session);
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String buildRemark(ReportProductionCommand command) {
        StringBuilder remark = new StringBuilder("生产报工；工序：")
                .append(command.process())
                .append("；操作人：")
                .append(command.operator());
        appendField(remark, "工序日期", command.workDate());
        appendField(remark, "工序类型", command.processCategory());
        appendField(remark, "品类", command.productCategory());
        appendField(remark, "数量单位", command.quantityUnit());
        appendField(remark, "机器编号", command.machineNo());
        appendField(remark, "操机签字", command.operatorSignatureName());
        appendField(remark, "工作项目", command.workItemName());
        appendField(remark, "开始时间", command.startedAt());
        appendField(remark, "完成时间", command.finishedAt());
        appendField(remark, "完成情况", command.completionStatus());
        appendField(remark, "交单审核", command.handoffAuditStatus());
        appendRegisterSource(remark, command.sourceRegisterType(), command.sourceRegisterLineNo(), command.sourceRegisterSequence());
        if (command.handoffCode() != null && !command.handoffCode().isBlank()) {
            remark.append("；交接：").append(command.handoffCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private String buildRemark(FreezeQualityStockCommand command) {
        StringBuilder remark = new StringBuilder("质检冻结；工序：")
                .append(command.process())
                .append("；质检员：")
                .append(command.inspector())
                .append("；缺陷：")
                .append(command.defect());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private String buildRemark(DispatchReworkCommand command) {
        StringBuilder remark = new StringBuilder("返工派发；质检单：")
                .append(command.qualityCaseCode())
                .append("；返工单：")
                .append(command.reworkOrderCode())
                .append("；工序：")
                .append(command.process())
                .append("；操作人：")
                .append(command.operator())
                .append("；原因：")
                .append(command.reason());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private String buildRemark(ReportReworkCommand command) {
        StringBuilder remark = new StringBuilder("返工报工；质检单：")
                .append(command.qualityCaseCode())
                .append("；返工单：")
                .append(command.reworkOrderCode())
                .append("；工序：")
                .append(command.process())
                .append("；操作人：")
                .append(command.operator());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private String buildRemark(ReleaseQualityStockCommand command) {
        StringBuilder remark = new StringBuilder("质检放行；质检单：")
                .append(command.qualityCaseCode())
                .append("；工序：")
                .append(command.process())
                .append("；质检员：")
                .append(command.inspector())
                .append("；结论：")
                .append(command.releaseResult());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private String buildRemark(ShipFinishedStockCommand command) {
        StringBuilder remark = new StringBuilder("成品出库；出库单：")
                .append(command.shipmentCode())
                .append("；物流单：")
                .append(command.logisticsNo())
                .append("；操作人：")
                .append(command.operator());
        appendField(remark, "交货日期", command.deliveryDate());
        appendField(remark, "交货类目", command.deliveryCategory());
        appendField(remark, "数量单位", command.quantityUnit());
        appendField(remark, "快递", command.expressCompany());
        appendField(remark, "纸质快递栏", command.paperExpressText());
        appendField(remark, "经办人", command.handler());
        appendRegisterSource(remark, command.sourceRegisterType(), command.sourceRegisterLineNo(), command.sourceRegisterSequence());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private String buildRemark(CustomerReturnCommand command) {
        StringBuilder remark = new StringBuilder("客户退货入库；退货单：")
                .append(command.returnCode())
                .append("；原出库单：")
                .append(command.originalShipmentCode())
                .append("；退回物流：")
                .append(command.returnLogisticsNo())
                .append("；原因：")
                .append(command.reason())
                .append("；操作人：")
                .append(command.operator());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private String buildRemark(AfterSalesReviewCommand command) {
        StringBuilder remark = new StringBuilder("售后退货复核；复核单：")
                .append(command.reviewCode())
                .append("；退货单：")
                .append(command.returnCode())
                .append("；原出库单：")
                .append(command.originalShipmentCode())
                .append("；分流：")
                .append(command.disposition())
                .append("；质检员：")
                .append(command.inspector())
                .append("；结论：")
                .append(command.finding());
        if (command.evidenceCode() != null && !command.evidenceCode().isBlank()) {
            remark.append("；证据：").append(command.evidenceCode());
        }
        if (command.remark() != null && !command.remark().isBlank()) {
            remark.append("；备注：").append(command.remark());
        }
        return remark.toString();
    }

    private StockStatus targetStatus(AfterSalesReviewDisposition disposition) {
        return switch (disposition) {
            case RELEASE -> StockStatus.FINISHED;
            case REWORK -> StockStatus.REWORK;
            case SCRAP -> StockStatus.SCRAP;
            case FREEZE -> StockStatus.FROZEN;
        };
    }

    private void appendField(StringBuilder remark, String label, String value) {
        if (value != null && !value.isBlank()) {
            remark.append("；").append(label).append("：").append(value);
        }
    }

    private void appendRegisterSource(StringBuilder remark, String type, Integer lineNo, String sequence) {
        appendField(remark, "来源登记表", type);
        if (lineNo != null) {
            remark.append("；原表行号：").append(lineNo);
        }
        appendField(remark, "纸质序号", sequence);
    }
}
