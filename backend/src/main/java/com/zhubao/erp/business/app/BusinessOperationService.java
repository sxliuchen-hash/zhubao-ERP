package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.business.domain.AfterSalesReviewCommand;
import com.zhubao.erp.business.domain.AfterSalesReviewDisposition;
import com.zhubao.erp.business.domain.AfterSalesReviewResult;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.CustomerReturnCommand;
import com.zhubao.erp.business.domain.CustomerReturnResult;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.business.domain.OutboundShipmentResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.QualityFreezeResult;
import com.zhubao.erp.business.domain.QualityReleaseResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ReportReworkCommand;
import com.zhubao.erp.business.domain.ReleaseQualityStockCommand;
import com.zhubao.erp.business.domain.ReworkDispatchResult;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class BusinessOperationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StockLedgerService stockLedgerService;
    private final ApprovalService approvalService;

    public BusinessOperationService(StockLedgerService stockLedgerService) {
        this(stockLedgerService, null);
    }

    @Autowired
    public BusinessOperationService(StockLedgerService stockLedgerService, ApprovalService approvalService) {
        this.stockLedgerService = stockLedgerService;
        this.approvalService = approvalService;
    }

    public InboundConfirmationResult confirmInbound(ConfirmInboundCommand command) {
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

        // Business operations append inventory events; balances stay derived from the ledger repository.
        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "inbound_confirm",
                command.motherOrderCode(),
                null,
                rawCustomerStock,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

        return new InboundConfirmationResult(
                command.motherOrderCode(),
                entry.code(),
                command.warehouseCode(),
                rawCustomerStock.stockStatus(),
                entry.quantity(),
                entry.weight(),
                command.operator(),
                command.evidenceCode()
        );
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

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "quality_freeze",
                command.qualityCaseCode(),
                fromSemiFinished,
                toFrozen,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

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

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "rework_dispatch",
                command.reworkOrderCode(),
                fromFrozen,
                toRework,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

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

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "rework_report",
                command.reworkOrderCode(),
                fromRework,
                toSemiFinished,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

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

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "quality_release",
                command.qualityCaseCode(),
                fromSemiFinished,
                toFinished,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

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

        StockLedgerEntry entry = stockLedgerService.transfer(new StockTransferCommand(
                "outbound_shipment",
                command.shipmentCode(),
                fromFinished,
                toShipped,
                command.quantity(),
                command.weight(),
                buildRemark(command)
        ));

        return new OutboundShipmentResult(
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
