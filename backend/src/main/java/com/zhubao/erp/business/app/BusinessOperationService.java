package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.QualityFreezeResult;
import com.zhubao.erp.business.domain.QualityReleaseResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ReportReworkCommand;
import com.zhubao.erp.business.domain.ReleaseQualityStockCommand;
import com.zhubao.erp.business.domain.ReworkDispatchResult;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import org.springframework.stereotype.Service;

@Service
public class BusinessOperationService {

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
}
