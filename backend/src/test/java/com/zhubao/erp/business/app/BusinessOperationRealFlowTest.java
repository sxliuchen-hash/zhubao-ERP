package com.zhubao.erp.business.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.AfterSalesReviewCommand;
import com.zhubao.erp.business.domain.AfterSalesReviewDisposition;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.CustomerReturnCommand;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.business.domain.ReleaseQualityStockCommand;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ReportReworkCommand;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessOperationRealFlowTest {

    private static final String FIXTURE = "acceptance/real-flow-scenario-20260706.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runsFixtureBackedCustomerOwnedProductionQualityShipmentReturnAndLossTrialScenario() throws IOException {
        RealFlowScenario scenario = loadScenario();
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);

        stockLedgerService.transfer(new StockTransferCommand(
                "split_work_order",
                scenario.workOrderCode(),
                null,
                dimension(scenario, StockStatus.WIP, scenario.initialWip().warehouseCode()),
                scenario.initialWip().quantity(),
                scenario.initialWip().weight(),
                scenario.sourceNote()
        ));

        ProductionReportFixture production = scenario.productionReport();
        service.reportProduction(new ReportProductionCommand(
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                production.fromWarehouseCode(),
                production.toWarehouseCode(),
                production.quantity(),
                production.weight(),
                production.process(),
                "人工",
                "戒托",
                "POL-01",
                production.process(),
                "13:40",
                "14:30",
                "已完成",
                "已接收",
                production.operator(),
                production.handoffCode(),
                production.remark()
        ));

        QualityFreezeFixture qualityFreeze = scenario.qualityFreeze();
        service.freezeQualityStock(new FreezeQualityStockCommand(
                qualityFreeze.qualityCaseCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                qualityFreeze.fromWarehouseCode(),
                qualityFreeze.toWarehouseCode(),
                qualityFreeze.quantity(),
                qualityFreeze.weight(),
                qualityFreeze.process(),
                qualityFreeze.inspector(),
                qualityFreeze.defect(),
                qualityFreeze.evidenceCode(),
                qualityFreeze.remark()
        ));

        ReworkDispatchFixture reworkDispatch = scenario.reworkDispatch();
        service.dispatchRework(new DispatchReworkCommand(
                reworkDispatch.reworkOrderCode(),
                reworkDispatch.qualityCaseCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                reworkDispatch.fromWarehouseCode(),
                reworkDispatch.toWarehouseCode(),
                reworkDispatch.quantity(),
                reworkDispatch.weight(),
                reworkDispatch.process(),
                reworkDispatch.operator(),
                reworkDispatch.reason(),
                reworkDispatch.evidenceCode(),
                reworkDispatch.remark()
        ));

        ReworkReportFixture reworkReport = scenario.reworkReport();
        service.reportRework(new ReportReworkCommand(
                reworkReport.reworkOrderCode(),
                reworkReport.qualityCaseCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                reworkReport.fromWarehouseCode(),
                reworkReport.toWarehouseCode(),
                reworkReport.quantity(),
                reworkReport.weight(),
                reworkReport.process(),
                reworkReport.operator(),
                reworkReport.evidenceCode(),
                reworkReport.remark()
        ));

        QualityReleaseFixture qualityRelease = scenario.qualityRelease();
        service.releaseQualityStock(new ReleaseQualityStockCommand(
                qualityRelease.qualityCaseCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                qualityRelease.fromWarehouseCode(),
                qualityRelease.toWarehouseCode(),
                qualityRelease.quantity(),
                qualityRelease.weight(),
                qualityRelease.process(),
                qualityRelease.inspector(),
                qualityRelease.releaseResult(),
                qualityRelease.evidenceCode(),
                qualityRelease.remark()
        ));

        OutboundShipmentFixture outboundShipment = scenario.outboundShipment();
        service.shipFinishedStock(new ShipFinishedStockCommand(
                outboundShipment.shipmentCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                outboundShipment.fromWarehouseCode(),
                outboundShipment.toWarehouseCode(),
                "2026-07-06",
                "成品",
                outboundShipment.quantity(),
                outboundShipment.weight(),
                "顺丰",
                outboundShipment.logisticsNo(),
                "何琳",
                outboundShipment.operator(),
                outboundShipment.evidenceCode(),
                outboundShipment.remark()
        ));

        CustomerReturnFixture customerReturn = scenario.customerReturn();
        service.returnCustomerShipment(new CustomerReturnCommand(
                customerReturn.returnCode(),
                customerReturn.originalShipmentCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                customerReturn.fromWarehouseCode(),
                customerReturn.toWarehouseCode(),
                customerReturn.quantity(),
                customerReturn.weight(),
                customerReturn.returnLogisticsNo(),
                customerReturn.reason(),
                customerReturn.operator(),
                customerReturn.evidenceCode(),
                customerReturn.remark()
        ));

        AfterSalesReviewFixture afterSalesReview = scenario.afterSalesReview();
        service.reviewAfterSalesReturn(new AfterSalesReviewCommand(
                afterSalesReview.reviewCode(),
                afterSalesReview.returnCode(),
                afterSalesReview.originalShipmentCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                afterSalesReview.fromWarehouseCode(),
                afterSalesReview.toWarehouseCode(),
                afterSalesReview.quantity(),
                afterSalesReview.weight(),
                AfterSalesReviewDisposition.valueOf(afterSalesReview.disposition()),
                afterSalesReview.inspector(),
                afterSalesReview.finding(),
                afterSalesReview.evidenceCode(),
                afterSalesReview.remark()
        ));

        LossApprovalFixture lossApprovalFixture = scenario.lossApproval();
        LossApprovalResult lossApproval = service.approveLoss(new ApproveLossCommand(
                lossApprovalFixture.approvalCode(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.customerId(),
                null,
                scenario.materialCode(),
                lossApprovalFixture.process(),
                lossApprovalFixture.operator(),
                lossApprovalFixture.approver(),
                lossApprovalFixture.inputWeight(),
                lossApprovalFixture.outputWeight(),
                lossApprovalFixture.reasonableTransferWeight(),
                lossApprovalFixture.standardLossRatePercent(),
                lossApprovalFixture.acceptedQuantity(),
                lossApprovalFixture.pieceRate(),
                lossApprovalFixture.lossDeductionUnitPrice(),
                lossApprovalFixture.evidenceCode(),
                lossApprovalFixture.remark()
        ));

        assertThat(repository.findAllLedgers())
                .extracting(StockLedgerEntry::documentType)
                .containsExactlyElementsOf(scenario.expectedLedgerTypes());
        assertAllLedgerDimensionsStayCustomerOwned(scenario, repository.findAllLedgers());
        for (ExpectedBalance expectedBalance : scenario.expectedBalances()) {
            assertBalance(scenario, repository, expectedBalance);
        }
        assertThat(lossApproval.lossWeight()).isEqualByComparingTo(scenario.expectedLossApproval().lossWeight());
        assertThat(lossApproval.excessLossWeight()).isEqualByComparingTo(scenario.expectedLossApproval().excessLossWeight());
        assertThat(lossApproval.trialWage()).isEqualByComparingTo(scenario.expectedLossApproval().trialWage());
        assertThat(lossApproval.formalPayroll()).isEqualTo(scenario.expectedLossApproval().formalPayroll());
        assertThat(repository.findAllLedgers()).hasSize(scenario.expectedLedgerTypes().size());
    }

    private RealFlowScenario loadScenario() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(FIXTURE)) {
            assertThat(inputStream).as("acceptance fixture exists: %s", FIXTURE).isNotNull();
            return objectMapper.readValue(inputStream, RealFlowScenario.class);
        }
    }

    private void assertAllLedgerDimensionsStayCustomerOwned(RealFlowScenario scenario, List<StockLedgerEntry> ledgers) {
        for (StockLedgerEntry ledger : ledgers) {
            if (ledger.from() != null) {
                assertSameBusinessDimension(scenario, ledger.from());
            }
            assertSameBusinessDimension(scenario, ledger.to());
        }
    }

    private void assertSameBusinessDimension(RealFlowScenario scenario, StockDimension dimension) {
        assertThat(dimension.ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(dimension.ownerCustomerId()).isEqualTo(scenario.customerId());
        assertThat(dimension.customerId()).isEqualTo(scenario.customerId());
        assertThat(dimension.motherOrderCode()).isEqualTo(scenario.motherOrderCode());
        assertThat(dimension.workOrderCode()).isEqualTo(scenario.workOrderCode());
        assertThat(dimension.materialCode()).isEqualTo(scenario.materialCode());
    }

    private void assertBalance(
            RealFlowScenario scenario,
            InMemoryStockLedgerRepository repository,
            ExpectedBalance expectedBalance
    ) {
        StockStatus stockStatus = StockStatus.valueOf(expectedBalance.stockStatus());
        StockBalance balance = repository.findBalance(dimension(scenario, stockStatus, expectedBalance.warehouseCode()));
        assertThat(balance.quantity()).isEqualByComparingTo(expectedBalance.quantity());
        assertThat(balance.weight()).isEqualByComparingTo(expectedBalance.weight());
    }

    private StockDimension dimension(RealFlowScenario scenario, StockStatus status, String warehouseCode) {
        return new StockDimension(
                OwnerType.CUSTOMER,
                scenario.customerId(),
                scenario.customerId(),
                scenario.motherOrderCode(),
                scenario.workOrderCode(),
                scenario.materialCode(),
                warehouseCode,
                status
        );
    }

    private record RealFlowScenario(
            String scenarioCode,
            String sourceNote,
            Long customerId,
            String motherOrderCode,
            String workOrderCode,
            String materialCode,
            StockFixture initialWip,
            ProductionReportFixture productionReport,
            QualityFreezeFixture qualityFreeze,
            ReworkDispatchFixture reworkDispatch,
            ReworkReportFixture reworkReport,
            QualityReleaseFixture qualityRelease,
            OutboundShipmentFixture outboundShipment,
            CustomerReturnFixture customerReturn,
            AfterSalesReviewFixture afterSalesReview,
            LossApprovalFixture lossApproval,
            List<String> expectedLedgerTypes,
            List<ExpectedBalance> expectedBalances,
            ExpectedLossApproval expectedLossApproval
    ) {
    }

    private record StockFixture(String warehouseCode, BigDecimal quantity, BigDecimal weight) {
    }

    private record ProductionReportFixture(
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String process,
            String operator,
            String handoffCode,
            String remark
    ) {
    }

    private record QualityFreezeFixture(
            String qualityCaseCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String process,
            String inspector,
            String defect,
            String evidenceCode,
            String remark
    ) {
    }

    private record ReworkDispatchFixture(
            String reworkOrderCode,
            String qualityCaseCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String process,
            String operator,
            String reason,
            String evidenceCode,
            String remark
    ) {
    }

    private record ReworkReportFixture(
            String reworkOrderCode,
            String qualityCaseCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String process,
            String operator,
            String evidenceCode,
            String remark
    ) {
    }

    private record QualityReleaseFixture(
            String qualityCaseCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String process,
            String inspector,
            String releaseResult,
            String evidenceCode,
            String remark
    ) {
    }

    private record OutboundShipmentFixture(
            String shipmentCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String logisticsNo,
            String operator,
            String evidenceCode,
            String remark
    ) {
    }

    private record CustomerReturnFixture(
            String returnCode,
            String originalShipmentCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String returnLogisticsNo,
            String reason,
            String operator,
            String evidenceCode,
            String remark
    ) {
    }

    private record AfterSalesReviewFixture(
            String reviewCode,
            String returnCode,
            String originalShipmentCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String disposition,
            String inspector,
            String finding,
            String evidenceCode,
            String remark
    ) {
    }

    private record LossApprovalFixture(
            String approvalCode,
            String process,
            String operator,
            String approver,
            BigDecimal inputWeight,
            BigDecimal outputWeight,
            BigDecimal reasonableTransferWeight,
            BigDecimal standardLossRatePercent,
            BigDecimal acceptedQuantity,
            BigDecimal pieceRate,
            BigDecimal lossDeductionUnitPrice,
            String evidenceCode,
            String remark
    ) {
    }

    private record ExpectedBalance(
            String stockStatus,
            String warehouseCode,
            BigDecimal quantity,
            BigDecimal weight
    ) {
    }

    private record ExpectedLossApproval(
            BigDecimal lossWeight,
            BigDecimal excessLossWeight,
            BigDecimal trialWage,
            boolean formalPayroll
    ) {
    }
}
