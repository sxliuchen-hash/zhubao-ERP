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

    @Test
    void freezeQualityStockMovesSemiFinishedToFrozenForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension semiFinished = dimension(StockStatus.SEMI_FINISHED, "SEMI-FINISHED");

        stockLedgerService.transfer(new StockTransferCommand(
                "production_report",
                "ZD-20260706-006",
                null,
                semiFinished,
                bd("210"),
                bd("478.240"),
                "测试预置半成品库存"
        ));

        QualityFreezeResult result = service.freezeQualityStock(new FreezeQualityStockCommand(
                "QC-20260706-022",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "SEMI-FINISHED",
                "FROZEN-QC",
                bd("6"),
                bd("13.660"),
                "焊接",
                "周娜",
                "焊点偏移 6 件",
                "PHOTO-QC-20260706-022",
                "质检异常冻结"
        ));

        StockDimension frozen = dimension(StockStatus.FROZEN, "FROZEN-QC");
        StockBalance semiFinishedBalance = repository.findBalance(semiFinished);
        StockBalance frozenBalance = repository.findBalance(frozen);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.qualityCaseCode()).isEqualTo("QC-20260706-022");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.SEMI_FINISHED);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.FROZEN);
        assertThat(result.evidenceCode()).isEqualTo("PHOTO-QC-20260706-022");
        assertThat(semiFinishedBalance.quantity()).isEqualByComparingTo("204");
        assertThat(semiFinishedBalance.weight()).isEqualByComparingTo("464.580");
        assertThat(frozenBalance.quantity()).isEqualByComparingTo("6");
        assertThat(frozenBalance.weight()).isEqualByComparingTo("13.660");
        assertThat(ledger.documentType()).isEqualTo("quality_freeze");
        assertThat(ledger.documentCode()).isEqualTo("QC-20260706-022");
        assertThat(ledger.remark()).contains("周娜", "焊点偏移 6 件", "PHOTO-QC-20260706-022");
    }

    @Test
    void dispatchReworkMovesFrozenToReworkForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension frozen = dimension(StockStatus.FROZEN, "FROZEN-QC");

        stockLedgerService.transfer(new StockTransferCommand(
                "quality_freeze",
                "QC-20260706-022",
                null,
                frozen,
                bd("6"),
                bd("13.660"),
                "测试预置冻结库存"
        ));

        ReworkDispatchResult result = service.dispatchRework(new DispatchReworkCommand(
                "FG-20260706-022",
                "QC-20260706-022",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "FROZEN-QC",
                "REWORK-LINE-01",
                bd("6"),
                bd("13.660"),
                "返工焊接",
                "车间组长 马杰",
                "焊点偏移返工",
                "PHOTO-QC-20260706-022",
                "冻结库存派发返工"
        ));

        StockDimension rework = dimension(StockStatus.REWORK, "REWORK-LINE-01");
        StockBalance frozenBalance = repository.findBalance(frozen);
        StockBalance reworkBalance = repository.findBalance(rework);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.reworkOrderCode()).isEqualTo("FG-20260706-022");
        assertThat(result.qualityCaseCode()).isEqualTo("QC-20260706-022");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.FROZEN);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.REWORK);
        assertThat(frozenBalance.quantity()).isEqualByComparingTo("0");
        assertThat(frozenBalance.weight()).isEqualByComparingTo("0.000");
        assertThat(reworkBalance.quantity()).isEqualByComparingTo("6");
        assertThat(reworkBalance.weight()).isEqualByComparingTo("13.660");
        assertThat(ledger.documentType()).isEqualTo("rework_dispatch");
        assertThat(ledger.documentCode()).isEqualTo("FG-20260706-022");
        assertThat(ledger.remark()).contains("QC-20260706-022", "车间组长 马杰", "焊点偏移返工");
    }

    @Test
    void reportReworkMovesReworkToSemiFinishedForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension rework = dimension(StockStatus.REWORK, "REWORK-LINE-01");

        stockLedgerService.transfer(new StockTransferCommand(
                "rework_dispatch",
                "FG-20260706-022",
                null,
                rework,
                bd("6"),
                bd("13.660"),
                "测试预置返工库存"
        ));

        ReworkReportResult result = service.reportRework(new ReportReworkCommand(
                "FG-20260706-022",
                "QC-20260706-022",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "REWORK-LINE-01",
                "SEMI-FINISHED",
                bd("6"),
                bd("13.660"),
                "返工焊接",
                "王强",
                "PHOTO-REWORK-20260706-022",
                "返工完成报工"
        ));

        StockDimension semiFinished = dimension(StockStatus.SEMI_FINISHED, "SEMI-FINISHED");
        StockBalance reworkBalance = repository.findBalance(rework);
        StockBalance semiFinishedBalance = repository.findBalance(semiFinished);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.reworkOrderCode()).isEqualTo("FG-20260706-022");
        assertThat(result.qualityCaseCode()).isEqualTo("QC-20260706-022");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.REWORK);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.SEMI_FINISHED);
        assertThat(reworkBalance.quantity()).isEqualByComparingTo("0");
        assertThat(reworkBalance.weight()).isEqualByComparingTo("0.000");
        assertThat(semiFinishedBalance.quantity()).isEqualByComparingTo("6");
        assertThat(semiFinishedBalance.weight()).isEqualByComparingTo("13.660");
        assertThat(ledger.documentType()).isEqualTo("rework_report");
        assertThat(ledger.documentCode()).isEqualTo("FG-20260706-022");
        assertThat(ledger.remark()).contains("QC-20260706-022", "返工焊接", "王强");
    }

    @Test
    void releaseQualityStockMovesSemiFinishedToFinishedForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension semiFinished = dimension(StockStatus.SEMI_FINISHED, "SEMI-FINISHED");

        stockLedgerService.transfer(new StockTransferCommand(
                "rework_report",
                "FG-20260706-022",
                null,
                semiFinished,
                bd("6"),
                bd("13.660"),
                "测试预置复检后半成品库存"
        ));

        QualityReleaseResult result = service.releaseQualityStock(new ReleaseQualityStockCommand(
                "QC-20260706-022",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "SEMI-FINISHED",
                "FINISHED-CUSTOMER",
                bd("6"),
                bd("13.660"),
                "复检焊接",
                "周娜",
                "复检合格，允许成品入库",
                "PHOTO-RELEASE-20260706-022",
                "复检放行转成品库存"
        ));

        StockDimension finished = dimension(StockStatus.FINISHED, "FINISHED-CUSTOMER");
        StockBalance semiFinishedBalance = repository.findBalance(semiFinished);
        StockBalance finishedBalance = repository.findBalance(finished);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.qualityCaseCode()).isEqualTo("QC-20260706-022");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.SEMI_FINISHED);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.FINISHED);
        assertThat(result.releaseResult()).contains("复检合格");
        assertThat(semiFinishedBalance.quantity()).isEqualByComparingTo("0");
        assertThat(semiFinishedBalance.weight()).isEqualByComparingTo("0.000");
        assertThat(finishedBalance.quantity()).isEqualByComparingTo("6");
        assertThat(finishedBalance.weight()).isEqualByComparingTo("13.660");
        assertThat(ledger.documentType()).isEqualTo("quality_release");
        assertThat(ledger.documentCode()).isEqualTo("QC-20260706-022");
        assertThat(ledger.remark()).contains("周娜", "复检合格", "PHOTO-RELEASE-20260706-022");
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
