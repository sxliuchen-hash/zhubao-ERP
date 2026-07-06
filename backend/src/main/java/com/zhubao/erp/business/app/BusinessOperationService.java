package com.zhubao.erp.business.app;

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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class BusinessOperationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StockLedgerService stockLedgerService;

    public BusinessOperationService(StockLedgerService stockLedgerService) {
        this.stockLedgerService = stockLedgerService;
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

        return new LossApprovalResult(
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
    }

    private String buildRemark(ConfirmInboundCommand command) {
        StringBuilder remark = new StringBuilder("确认来料入库；操作人：").append(command.operator());
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
}
