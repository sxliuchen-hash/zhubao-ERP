package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.domain.AfterSalesReviewCommand;
import com.zhubao.erp.business.domain.AfterSalesReviewDisposition;
import com.zhubao.erp.business.domain.AfterSalesReviewResult;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.CustomerReturnCommand;
import com.zhubao.erp.business.domain.CustomerReturnResult;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.ApproveLossCommand;
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
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                "2026-07-06",
                "顺丰",
                "SF1048329001",
                "张先生",
                "何琳",
                "何琳",
                "戒托来料",
                "签收入库登记表",
                4,
                "1",
                "RAW-CUSTOMER",
                bd("320"),
                "件",
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
        assertThat(ledger.remark()).contains("库管员 何琳", "2026-07-06", "顺丰", "张先生", "拆箱签字", "戒托来料", "数量单位", "件", "来源登记表", "签收入库登记表", "原表行号", "4", "纸质序号", "1", "SF1048329001", "拆包称重确认");
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
    void confirmInboundPersistsStructuredMotherOrderWhenDocumentRepositoryIsProvided() {
        InMemoryStockLedgerRepository stockRepository = new InMemoryStockLedgerRepository();
        InMemoryBusinessDocumentRepository documentRepository = new InMemoryBusinessDocumentRepository();
        BusinessOperationService service = new BusinessOperationService(
                new StockLedgerService(stockRepository),
                null,
                documentRepository
        );

        InboundConfirmationResult result = service.confirmInbound(new ConfirmInboundCommand(
                "MD-DOC-20260708-001",
                1001L,
                null,
                "MAT-18K-RING",
                "2026-07-08",
                "顺丰",
                "SF-DOC-001",
                "张先生",
                "何琳",
                "何琳",
                "戒托来料",
                "签收入库登记表",
                5,
                "2",
                "RAW-CUSTOMER",
                bd("12"),
                "件",
                bd("18.500"),
                "库管员 何琳",
                "ATT-IN-DOC-001",
                "结构化母单测试"
        ));

        assertThat(documentRepository.findMotherOrderByCode("MD-DOC-20260708-001"))
                .hasValueSatisfying(record -> {
                    assertThat(record.ledgerCode()).isEqualTo(result.ledgerCode());
                    assertThat(record.customerOwned()).isTrue();
                    assertThat(record.stockStatus()).isEqualTo("RAW_WAITING");
                    assertThat(record.quantity()).isEqualByComparingTo("12");
                    assertThat(record.weight()).isEqualByComparingTo("18.500");
                    assertThat(record.sourceRegisterType()).isEqualTo("签收入库登记表");
                    assertThat(record.sourceRegisterLineNo()).isEqualTo(5);
                });
        assertThat(stockRepository.findAllLedgers()).hasSize(1);

        assertThatThrownBy(() -> service.confirmInbound(new ConfirmInboundCommand(
                "MD-DOC-20260708-001",
                1001L,
                null,
                "MAT-18K-RING",
                "2026-07-08",
                "顺丰",
                "SF-DOC-001",
                "张先生",
                "何琳",
                "何琳",
                "戒托来料",
                "签收入库登记表",
                5,
                "2",
                "RAW-CUSTOMER",
                bd("12"),
                "件",
                bd("18.500"),
                "库管员 何琳",
                "ATT-IN-DOC-001",
                "重复结构化母单测试"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("结构化母单已存在");
        assertThat(stockRepository.findAllLedgers()).hasSize(1);
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
                "件",
                bd("478.240"),
                "2026-07-06",
                "焊接",
                "人工",
                "银链",
                "WELD-02",
                "焊接",
                "15:00",
                "进行中",
                "称重待补",
                "待交单审核",
                "工作项目记录单（日）",
                12,
                "1",
                "王强",
                null,
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
        assertThat(ledger.remark()).contains("焊接", "王强", "WELD-02", "银链", "数量单位", "件", "交单审核", "来源登记表", "工作项目记录单（日）", "原表行号", "12", "纸质序号", "1", "HO-20260706-018");
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

    @Test
    void shipFinishedStockMovesFinishedToShippedForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension finished = dimension(StockStatus.FINISHED, "FINISHED-CUSTOMER");

        stockLedgerService.transfer(new StockTransferCommand(
                "quality_release",
                "QC-20260706-022",
                null,
                finished,
                bd("6"),
                bd("13.660"),
                "测试预置成品库存"
        ));

        OutboundShipmentResult result = service.shipFinishedStock(new ShipFinishedStockCommand(
                "OUT-20260706-022",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "FINISHED-CUSTOMER",
                "SHIPPED-CUSTOMER",
                "2026-07-06",
                "成品",
                bd("6"),
                "件",
                bd("13.660"),
                "顺丰",
                "SF-OUT-20260706-022",
                "顺丰 SF-OUT-20260706-022",
                "交货情况登记表",
                3,
                "1",
                "何琳",
                "库管员 何琳",
                "ATT-OUT-20260706-022",
                "客户成品出库"
        ));

        StockDimension shipped = dimension(StockStatus.SHIPPED, "SHIPPED-CUSTOMER");
        StockBalance finishedBalance = repository.findBalance(finished);
        StockBalance shippedBalance = repository.findBalance(shipped);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.shipmentCode()).isEqualTo("OUT-20260706-022");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.FINISHED);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.SHIPPED);
        assertThat(result.logisticsNo()).isEqualTo("SF-OUT-20260706-022");
        assertThat(finishedBalance.quantity()).isEqualByComparingTo("0");
        assertThat(finishedBalance.weight()).isEqualByComparingTo("0.000");
        assertThat(shippedBalance.quantity()).isEqualByComparingTo("6");
        assertThat(shippedBalance.weight()).isEqualByComparingTo("13.660");
        assertThat(ledger.documentType()).isEqualTo("outbound_shipment");
        assertThat(ledger.documentCode()).isEqualTo("OUT-20260706-022");
        assertThat(ledger.remark()).contains("2026-07-06", "成品", "数量单位", "件", "顺丰", "纸质快递栏", "顺丰 SF-OUT-20260706-022", "何琳", "来源登记表", "交货情况登记表", "原表行号", "3", "纸质序号", "1", "SF-OUT-20260706-022", "库管员 何琳", "ATT-OUT-20260706-022");
    }

    @Test
    void shipFinishedStockPersistsStructuredOutboundOrderWhenDocumentRepositoryIsProvided() {
        InMemoryStockLedgerRepository stockRepository = new InMemoryStockLedgerRepository();
        InMemoryBusinessDocumentRepository documentRepository = new InMemoryBusinessDocumentRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(stockRepository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService, null, documentRepository);
        StockDimension finished = dimension(StockStatus.FINISHED, "FINISHED-CUSTOMER");

        stockLedgerService.transfer(new StockTransferCommand(
                "quality_release",
                "QC-DOC-20260708-001",
                null,
                finished,
                bd("6"),
                bd("13.660"),
                "测试预置成品库存"
        ));

        OutboundShipmentResult result = service.shipFinishedStock(new ShipFinishedStockCommand(
                "OUT-DOC-20260708-001",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "FINISHED-CUSTOMER",
                "SHIPPED-CUSTOMER",
                "2026-07-08",
                "成品",
                bd("6"),
                "件",
                bd("13.660"),
                "顺丰",
                "SF-OUT-DOC-001",
                "顺丰 SF-OUT-DOC-001",
                "交货情况登记表",
                7,
                "3",
                "何琳",
                "库管员 何琳",
                "ATT-OUT-DOC-001",
                "结构化出库单测试"
        ));

        assertThat(documentRepository.findOutboundOrderByCode("OUT-DOC-20260708-001"))
                .hasValueSatisfying(record -> {
                    assertThat(record.ledgerCode()).isEqualTo(result.ledgerCode());
                    assertThat(record.motherOrderCode()).isEqualTo("MD-20260706-002");
                    assertThat(record.workOrderCode()).isEqualTo("ZD-20260706-006");
                    assertThat(record.status()).isEqualTo("SHIPPED");
                    assertThat(record.logisticsNo()).isEqualTo("SF-OUT-DOC-001");
                    assertThat(record.sourceRegisterLineNo()).isEqualTo(7);
                });
        assertThat(stockRepository.findAllLedgers()).hasSize(2);

        assertThatThrownBy(() -> service.shipFinishedStock(new ShipFinishedStockCommand(
                "OUT-DOC-20260708-001",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "FINISHED-CUSTOMER",
                "SHIPPED-CUSTOMER",
                "2026-07-08",
                "成品",
                bd("1"),
                "件",
                bd("1.000"),
                "顺丰",
                "SF-OUT-DOC-001",
                "顺丰 SF-OUT-DOC-001",
                "交货情况登记表",
                7,
                "3",
                "何琳",
                "库管员 何琳",
                "ATT-OUT-DOC-001",
                "重复结构化出库单测试"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("结构化出库单已存在");
        assertThat(stockRepository.findAllLedgers()).hasSize(2);
    }

    @Test
    void returnCustomerShipmentMovesShippedBackToFinishedForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension shipped = dimension(StockStatus.SHIPPED, "SHIPPED-CUSTOMER");

        stockLedgerService.transfer(new StockTransferCommand(
                "outbound_shipment",
                "OUT-20260706-022",
                null,
                shipped,
                bd("6"),
                bd("13.660"),
                "测试预置已发货库存"
        ));

        CustomerReturnResult result = service.returnCustomerShipment(new CustomerReturnCommand(
                "RET-20260707-022",
                "OUT-20260706-022",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "SHIPPED-CUSTOMER",
                "RETURN-CHECK",
                bd("2"),
                bd("4.550"),
                "SF-RET-20260707-022",
                "客户退回复核",
                "库管员 何琳",
                "ATT-RET-20260707-022",
                "客户退货待复核"
        ));

        StockDimension returnedFinished = dimension(StockStatus.FINISHED, "RETURN-CHECK");
        StockBalance shippedBalance = repository.findBalance(shipped);
        StockBalance returnedFinishedBalance = repository.findBalance(returnedFinished);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.returnCode()).isEqualTo("RET-20260707-022");
        assertThat(result.originalShipmentCode()).isEqualTo("OUT-20260706-022");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.SHIPPED);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.FINISHED);
        assertThat(result.returnLogisticsNo()).isEqualTo("SF-RET-20260707-022");
        assertThat(shippedBalance.quantity()).isEqualByComparingTo("4");
        assertThat(shippedBalance.weight()).isEqualByComparingTo("9.110");
        assertThat(returnedFinishedBalance.quantity()).isEqualByComparingTo("2");
        assertThat(returnedFinishedBalance.weight()).isEqualByComparingTo("4.550");
        assertThat(ledger.documentType()).isEqualTo("customer_return");
        assertThat(ledger.documentCode()).isEqualTo("RET-20260707-022");
        assertThat(ledger.remark()).contains("OUT-20260706-022", "SF-RET-20260707-022", "客户退回复核", "ATT-RET-20260707-022");
    }

    @Test
    void reviewAfterSalesReturnMovesReturnCheckStockToReworkForSameWorkOrderDimension() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);
        StockDimension returnCheck = dimension(StockStatus.FINISHED, "RETURN-CHECK");

        stockLedgerService.transfer(new StockTransferCommand(
                "customer_return",
                "RET-20260707-022",
                null,
                returnCheck,
                bd("2"),
                bd("4.550"),
                "测试预置退货复核库存"
        ));

        AfterSalesReviewResult result = service.reviewAfterSalesReturn(new AfterSalesReviewCommand(
                "ASR-20260707-022",
                "RET-20260707-022",
                "OUT-20260706-022",
                "MD-20260706-002",
                "ZD-20260706-006",
                1002L,
                null,
                "MAT-SILVER-CHAIN",
                "RETURN-CHECK",
                "REWORK-LINE-01",
                bd("2"),
                bd("4.550"),
                AfterSalesReviewDisposition.REWORK,
                "周娜",
                "客户退回件焊点偏移，转返工",
                "PHOTO-ASR-20260707-022",
                "售后复核分流"
        ));

        StockDimension rework = dimension(StockStatus.REWORK, "REWORK-LINE-01");
        StockBalance returnCheckBalance = repository.findBalance(returnCheck);
        StockBalance reworkBalance = repository.findBalance(rework);
        StockLedgerEntry ledger = repository.findAllLedgers().get(1);

        assertThat(result.reviewCode()).isEqualTo("ASR-20260707-022");
        assertThat(result.returnCode()).isEqualTo("RET-20260707-022");
        assertThat(result.fromStockStatus()).isEqualTo(StockStatus.FINISHED);
        assertThat(result.toStockStatus()).isEqualTo(StockStatus.REWORK);
        assertThat(result.disposition()).isEqualTo(AfterSalesReviewDisposition.REWORK);
        assertThat(returnCheckBalance.quantity()).isEqualByComparingTo("0");
        assertThat(returnCheckBalance.weight()).isEqualByComparingTo("0.000");
        assertThat(reworkBalance.quantity()).isEqualByComparingTo("2");
        assertThat(reworkBalance.weight()).isEqualByComparingTo("4.550");
        assertThat(ledger.documentType()).isEqualTo("after_sales_review");
        assertThat(ledger.documentCode()).isEqualTo("ASR-20260707-022");
        assertThat(ledger.remark()).contains(
                "RET-20260707-022",
                "OUT-20260706-022",
                "REWORK",
                "周娜",
                "PHOTO-ASR-20260707-022"
        );
    }

    @Test
    void approveLossCalculatesOverLimitLossAndWageTrialSnapshot() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        BusinessOperationService service = new BusinessOperationService(new StockLedgerService(repository));

        LossApprovalResult result = service.approveLoss(new ApproveLossCommand(
                "LOSS-20260706-009",
                "MD-20260706-001",
                "ZD-20260706-001",
                1001L,
                null,
                "MAT-18K-RING",
                "抛光",
                "李华",
                "车间组长 马杰",
                bd("121.150"),
                bd("116.550"),
                bd("0.000"),
                bd("2.0"),
                bd("56"),
                bd("0.80"),
                bd("6.00"),
                "HO-20260706-021",
                "超标损耗审批 smoke"
        ));

        assertThat(result.approvalCode()).isEqualTo("LOSS-20260706-009");
        assertThat(result.lossWeight()).isEqualByComparingTo("4.600");
        assertThat(result.allowedLossWeight()).isEqualByComparingTo("2.423");
        assertThat(result.excessLossWeight()).isEqualByComparingTo("2.177");
        assertThat(result.actualLossRatePercent()).isEqualByComparingTo("3.80");
        assertThat(result.overLimit()).isTrue();
        assertThat(result.approvalStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(result.pieceWage()).isEqualByComparingTo("44.80");
        assertThat(result.lossDeduction()).isEqualByComparingTo("13.06");
        assertThat(result.trialWage()).isEqualByComparingTo("31.74");
        assertThat(result.formalPayroll()).isFalse();
        assertThat(repository.findAllLedgers()).isEmpty();
    }

    @Test
    void approveLossCreatesLightweightApprovalTaskWhenApprovalServiceIsProvided() {
        InMemoryStockLedgerRepository stockRepository = new InMemoryStockLedgerRepository();
        InMemoryApprovalTaskRepository approvalRepository = new InMemoryApprovalTaskRepository();
        BusinessOperationService service = new BusinessOperationService(
                new StockLedgerService(stockRepository),
                new ApprovalService(approvalRepository)
        );

        LossApprovalResult result = service.approveLoss(new ApproveLossCommand(
                "LOSS-20260706-099",
                "MD-20260706-099",
                "ZD-20260706-099",
                1001L,
                null,
                "MAT-18K-RING",
                "polish",
                "operator-a",
                "leader-a",
                bd("121.150"),
                bd("116.550"),
                bd("0.000"),
                bd("2.0"),
                bd("56"),
                bd("0.80"),
                bd("6.00"),
                "HO-20260706-099",
                "loss approval task linkage"
        ));

        assertThat(result.approvalStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(stockRepository.findAllLedgers()).isEmpty();
        assertThat(approvalRepository.findAll()).singleElement().satisfies(task -> {
            assertThat(task.approvalCode()).isEqualTo("LOSS-20260706-099");
            assertThat(task.status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
            assertThat(task.businessCode()).isEqualTo("ZD-20260706-099");
            assertThat(task.formalPayroll()).isFalse();
            assertThat(task.sourceSummary()).contains("excessLossWeight=2.177", "trialWage=31.74");
        });
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
