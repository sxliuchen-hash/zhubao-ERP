package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
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
}
