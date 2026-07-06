package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessOperationServiceTest {

    @Test
    void confirmInboundWritesCustomerOwnedRawStockLedger() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        BusinessOperationService service = new BusinessOperationService(new StockLedgerService(repository));

        InboundConfirmationResult result = service.confirmInbound(new ConfirmInboundCommand(
                "MD-20260706-001",
                1001L,
                null,
                "MAT-18K-RING",
                "RAW-CUSTOMER",
                bd("320"),
                bd("486.250"),
                "库管员 何琳",
                "SF1048329001",
                "拆包称重确认"
        ));

        assertThat(result.motherOrderCode()).isEqualTo("MD-20260706-001");
        assertThat(result.stockStatus()).isEqualTo(StockStatus.RAW_WAITING);
        assertThat(result.quantity()).isEqualByComparingTo("320");
        assertThat(result.weight()).isEqualByComparingTo("486.250");

        StockLedgerEntry ledger = repository.findAllLedgers().get(0);
        assertThat(ledger.documentType()).isEqualTo("inbound_confirm");
        assertThat(ledger.documentCode()).isEqualTo("MD-20260706-001");
        assertThat(ledger.remark()).contains("库管员 何琳", "SF1048329001", "拆包称重确认");
        assertThat(ledger.to().ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(ledger.to().ownerCustomerId()).isEqualTo(1001L);
        assertThat(ledger.to().customerId()).isEqualTo(1001L);
        assertThat(ledger.to().workOrderCode()).isNull();

        StockBalance balance = repository.findAllBalances().get(0);
        assertThat(balance.dimension().motherOrderCode()).isEqualTo("MD-20260706-001");
        assertThat(balance.dimension().stockStatus()).isEqualTo(StockStatus.RAW_WAITING);
        assertThat(balance.quantity()).isEqualByComparingTo("320");
        assertThat(balance.weight()).isEqualByComparingTo("486.250");
    }

    @Test
    void reportProductionMovesWipToSemiFinishedForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension wip = dimension(StockStatus.WIP, "WIP-LINE-01");

        stockLedgerService.transfer(new StockTransferCommand(
                "split_work_order",
                "ZD-20260706-006",
                null,
                wip,
                bd("300"),
                bd("683.200"),
                "测试预置在制库存"
        ));

        ProductionReportResult result = service.reportProduction(new ReportProductionCommand(
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "WIP-LINE-01",
                "SEMI-FINISHED",
                bd("210"),
                bd("478.240"),
                "焊接",
                "王强",
                "HO-20260706-018",
                "扫码报工确认"
        ));

        StockDimension semiFinished = dimension(StockStatus.SEMI_FINISHED, "SEMI-FINISHED");
        StockBalance wipBalance = repository.findBalance(wip);
        StockBalance semiFinishedBalance = repository.findBalance(semiFinished);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.workOrderCode()).isEqualTo("ZD-20260706-006");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.WIP);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.SEMI_FINISHED);
        assertThat(result.handoffCode()).isEqualTo("HO-20260706-018");
        assertThat(wipBalance.quantity()).isEqualByComparingTo("90");
        assertThat(wipBalance.weight()).isEqualByComparingTo("204.960");
        assertThat(semiFinishedBalance.quantity()).isEqualByComparingTo("210");
        assertThat(semiFinishedBalance.weight()).isEqualByComparingTo("478.240");
        assertThat(ledger.documentType()).isEqualTo("production_report");
        assertThat(ledger.remark()).contains("焊接", "王强", "HO-20260706-018");
    }

    private StockDimension dimension(StockStatus status, String warehouseCode) {
        return new StockDimension(
                OwnerType.CUSTOMER,
                1002L,
                1002L,
                "MD-20260706-002",
                "ZD-20260706-006",
                "MAT-SILVER-CHAIN",
                warehouseCode,
                status
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
