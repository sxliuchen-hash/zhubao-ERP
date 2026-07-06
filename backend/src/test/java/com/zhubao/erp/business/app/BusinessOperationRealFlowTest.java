package com.zhubao.erp.business.app;

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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessOperationRealFlowTest {

    private static final long CUSTOMER_ID = 1001L;
    private static final String MOTHER_ORDER = "MD-20260706-001";
    private static final String WORK_ORDER = "ZD-20260706-001";
    private static final String MATERIAL = "MAT-18K-RING";

    @Test
    void runsCustomerOwnedProductionQualityShipmentReturnAndLossTrialScenario() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stockLedgerService = new StockLedgerService(repository);
        BusinessOperationService service = new BusinessOperationService(stockLedgerService);

        stockLedgerService.transfer(new StockTransferCommand(
                "split_work_order",
                WORK_ORDER,
                null,
                dimension(StockStatus.WIP, "WIP-POLISH"),
                bd("80"),
                bd("121.150"),
                "真实链路测试预置在制库存"
        ));

        service.reportProduction(new ReportProductionCommand(
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "WIP-POLISH",
                "SEMI-FINISHED",
                bd("76"),
                bd("116.550"),
                "抛光",
                "李华",
                "HO-20260706-021",
                "真实链路测试：抛光报工"
        ));

        service.freezeQualityStock(new FreezeQualityStockCommand(
                "QC-20260706-021",
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "SEMI-FINISHED",
                "FROZEN-QC",
                bd("5"),
                bd("7.700"),
                "抛光",
                "周娜",
                "划痕 5 件",
                "PHOTO-QC-20260706-021",
                "真实链路测试：质检冻结"
        ));

        service.dispatchRework(new DispatchReworkCommand(
                "FG-20260706-021",
                "QC-20260706-021",
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "FROZEN-QC",
                "REWORK-POLISH",
                bd("5"),
                bd("7.700"),
                "返工抛光",
                "车间组长 马杰",
                "划痕返工",
                "PHOTO-QC-20260706-021",
                "真实链路测试：返工派发"
        ));

        service.reportRework(new ReportReworkCommand(
                "FG-20260706-021",
                "QC-20260706-021",
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "REWORK-POLISH",
                "SEMI-FINISHED",
                bd("5"),
                bd("7.700"),
                "返工抛光",
                "赵磊",
                "PHOTO-REWORK-20260706-021",
                "真实链路测试：返工报工"
        ));

        service.releaseQualityStock(new ReleaseQualityStockCommand(
                "QC-20260706-021",
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "SEMI-FINISHED",
                "FINISHED-CUSTOMER",
                bd("76"),
                bd("116.550"),
                "复检抛光",
                "周娜",
                "复检合格，允许成品入库",
                "PHOTO-RELEASE-20260706-021",
                "真实链路测试：复检放行"
        ));

        service.shipFinishedStock(new ShipFinishedStockCommand(
                "OUT-20260706-021",
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "FINISHED-CUSTOMER",
                "SHIPPED-CUSTOMER",
                bd("70"),
                bd("107.250"),
                "SF-OUT-20260706-021",
                "库管员 何琳",
                "ATT-OUT-20260706-021",
                "真实链路测试：成品出库"
        ));

        service.returnCustomerShipment(new CustomerReturnCommand(
                "RET-20260707-021",
                "OUT-20260706-021",
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "SHIPPED-CUSTOMER",
                "RETURN-CHECK",
                bd("2"),
                bd("3.100"),
                "SF-RET-20260707-021",
                "客户退回复核",
                "库管员 何琳",
                "ATT-RET-20260707-021",
                "真实链路测试：退货入库"
        ));

        LossApprovalResult lossApproval = service.approveLoss(new ApproveLossCommand(
                "LOSS-20260706-009",
                MOTHER_ORDER,
                WORK_ORDER,
                CUSTOMER_ID,
                null,
                MATERIAL,
                "抛光",
                "李华",
                "车间组长 马杰",
                bd("121.150"),
                bd("116.550"),
                bd("0.000"),
                bd("2.0"),
                bd("76"),
                bd("0.80"),
                bd("6.00"),
                "HO-20260706-021",
                "真实链路测试：损耗试算"
        ));

        assertThat(repository.findAllLedgers())
                .extracting(StockLedgerEntry::documentType)
                .containsExactly(
                        "split_work_order",
                        "production_report",
                        "quality_freeze",
                        "rework_dispatch",
                        "rework_report",
                        "quality_release",
                        "outbound_shipment",
                        "customer_return"
                );
        assertAllLedgerDimensionsStayCustomerOwned(repository.findAllLedgers());
        assertBalance(repository, StockStatus.WIP, "WIP-POLISH", "4", "4.600");
        assertBalance(repository, StockStatus.SEMI_FINISHED, "SEMI-FINISHED", "0", "0.000");
        assertBalance(repository, StockStatus.FROZEN, "FROZEN-QC", "0", "0.000");
        assertBalance(repository, StockStatus.REWORK, "REWORK-POLISH", "0", "0.000");
        assertBalance(repository, StockStatus.FINISHED, "FINISHED-CUSTOMER", "6", "9.300");
        assertBalance(repository, StockStatus.SHIPPED, "SHIPPED-CUSTOMER", "68", "104.150");
        assertBalance(repository, StockStatus.FINISHED, "RETURN-CHECK", "2", "3.100");
        assertThat(lossApproval.lossWeight()).isEqualByComparingTo("4.600");
        assertThat(lossApproval.excessLossWeight()).isEqualByComparingTo("2.177");
        assertThat(lossApproval.trialWage()).isEqualByComparingTo("47.74");
        assertThat(lossApproval.formalPayroll()).isFalse();
        assertThat(repository.findAllLedgers()).hasSize(8);
    }

    private void assertAllLedgerDimensionsStayCustomerOwned(List<StockLedgerEntry> ledgers) {
        for (StockLedgerEntry ledger : ledgers) {
            if (ledger.from() != null) {
                assertSameBusinessDimension(ledger.from());
            }
            assertSameBusinessDimension(ledger.to());
        }
    }

    private void assertSameBusinessDimension(StockDimension dimension) {
        assertThat(dimension.ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(dimension.ownerCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(dimension.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(dimension.motherOrderCode()).isEqualTo(MOTHER_ORDER);
        assertThat(dimension.workOrderCode()).isEqualTo(WORK_ORDER);
        assertThat(dimension.materialCode()).isEqualTo(MATERIAL);
    }

    private void assertBalance(
            InMemoryStockLedgerRepository repository,
            StockStatus status,
            String warehouseCode,
            String expectedQuantity,
            String expectedWeight
    ) {
        StockBalance balance = repository.findBalance(dimension(status, warehouseCode));
        assertThat(balance.quantity()).isEqualByComparingTo(expectedQuantity);
        assertThat(balance.weight()).isEqualByComparingTo(expectedWeight);
    }

    private StockDimension dimension(StockStatus status, String warehouseCode) {
        return new StockDimension(
                OwnerType.CUSTOMER,
                CUSTOMER_ID,
                CUSTOMER_ID,
                MOTHER_ORDER,
                WORK_ORDER,
                MATERIAL,
                warehouseCode,
                status
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
