package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.domain.BusinessReviewData.OperationContext;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 不拆母单必须按实际库存阶段保留工单维度，不能把加工中的母单号改写为空。 */
class KeptMotherOperationContextTest {

    private static final String MOTHER = "MD-KEEP-CONTEXT";
    private static final String RAW = "RAW-CUSTOMER";

    @ParameterizedTest(name = "不拆母单收货至出库上下文，JDBC={0}")
    @ValueSource(booleans = {false, true})
    void keepsActualContextAcrossReceiptFirstReportQualityAndShipment(boolean jdbc) throws SQLException {
        Flow flow = flow(jdbc);
        assertContext(flow.workspaces().inbound().orders().get(0).operationContext(), "", RAW, StockStatus.RAW_WAITING);
        flow.splits().keep(MOTHER, "收货员");
        assertContext(flow.workspaces().production().workOrders().get(0).operationContext(), "", RAW, StockStatus.RAW_WAITING);

        flow.stations().report(MOTHER, "雕刻", worker("雕刻"));
        assertContext(flow.workspaces().production().workOrders().get(0).operationContext(), MOTHER, RAW, StockStatus.WIP);
        assertThat(flow.workspaces().inbound().orders().get(0).operationContext()).isNull();
        flow.stations().report(MOTHER, "镶嵌", worker("镶嵌"));
        assertContext(flow.workspaces().production().workOrders().get(0).operationContext(), MOTHER, RAW, StockStatus.WIP);
        flow.stations().report(MOTHER, "抛光", worker("抛光"));
        assertContext(flow.workspaces().quality().cases().get(0).operationContext(), MOTHER, RAW, StockStatus.SEMI_FINISHED);

        var check = flow.quality().submit(new QualityCheckCommand(MOTHER, null,
                List.of(new QualityCheckLineCommand(1, QualityVerdicts.PASS, ""))), inspector());
        assertContext(flow.workspaces().quality().cases().get(0).operationContext(), MOTHER, RAW, StockStatus.SEMI_FINISHED);
        flow.quality().release(new ReleaseQualityStockCommand(check.qualityCaseCode(), MOTHER, MOTHER, 7001L, 7001L,
                "MAT-REAL", RAW, "FINISHED", bd("2"), bd("12.5"), "抛光", null, "合格", "", ""), inspector());
        assertContext(flow.workspaces().quality().cases().get(0).operationContext(), MOTHER, "FINISHED", StockStatus.FINISHED);

        flow.operations().shipFinishedStock(new ShipFinishedStockCommand("CK-KEEP-CONTEXT", MOTHER, MOTHER, 7001L, 7001L,
                "MAT-REAL", "FINISHED", "SHIPPED", "2026-10-10", "成品", bd("2"), "件", bd("12.5"),
                "顺丰", "SF123456789012", "", "", null, "", "库管员", "库管员", "", ""));
        assertThat(flow.workspaces().inventory().balances()).singleElement().satisfies(balance ->
                assertContext(balance.operationContext(), MOTHER, "SHIPPED", StockStatus.SHIPPED));
    }

    @ParameterizedTest(name = "不拆母单冻结与返工上下文，JDBC={0}")
    @ValueSource(booleans = {false, true})
    void keepsMotherWorkOrderForFrozenAndReworkStock(boolean jdbc) throws SQLException {
        Flow flow = flow(jdbc);
        flow.splits().keep(MOTHER, "收货员");
        finish(flow);
        flow.quality().submit(new QualityCheckCommand(MOTHER, null,
                List.of(new QualityCheckLineCommand(1, QualityVerdicts.FAIL, "表面划伤"))), inspector());
        assertContext(flow.workspaces().quality().cases().get(0).operationContext(), MOTHER, RAW, StockStatus.FROZEN);
        flow.quality().openRework(MOTHER, inspector());
        assertContext(flow.workspaces().quality().cases().get(0).operationContext(), MOTHER, RAW, StockStatus.REWORK);
        assertContext(flow.workspaces().production().workOrders().get(0).operationContext(), MOTHER, RAW, StockStatus.REWORK);
    }

    @ParameterizedTest(name = "不拆母单多仓仍阻断来源猜测，JDBC={0}")
    @ValueSource(booleans = {false, true})
    void doesNotChooseOneWarehouseAfterFirstReport(boolean jdbc) throws SQLException {
        Flow flow = flow(jdbc);
        flow.splits().keep(MOTHER, "收货员");
        flow.stations().report(MOTHER, "雕刻", worker("雕刻"));
        StockDimension other = new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, MOTHER, MOTHER,
                "MAT-REAL", "WIP-OTHER", StockStatus.WIP);
        flow.stocks().transfer(new StockTransferCommand("inventory_adjustment", "ADJUST-KEEP", null, other,
                BigDecimal.ONE, BigDecimal.ONE, "另一实际库位"));
        assertThat(flow.workspaces().production().workOrders()).singleElement()
                .satisfies(order -> assertThat(order.operationContext()).isNull());
        assertThat(flow.workspaces().inventory().balances()).hasSize(2)
                .allSatisfy(balance -> assertThat(balance.operationContext()).isNotNull());
    }

    @ParameterizedTest(name = "历史空工单与母单工单并存时不猜测，JDBC={0}")
    @ValueSource(booleans = {false, true})
    void doesNotChooseBetweenLegacyEmptyWorkOrderAndMotherWorkOrder(boolean jdbc) throws SQLException {
        Flow flow = flow(jdbc);
        flow.splits().keep(MOTHER, "收货员");
        flow.stations().report(MOTHER, "雕刻", worker("雕刻"));
        StockDimension legacy = new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, MOTHER, null,
                "MAT-REAL", RAW, StockStatus.WIP);
        flow.stocks().transfer(new StockTransferCommand("inventory_adjustment", "LEGACY-KEEP", null, legacy,
                BigDecimal.ONE, BigDecimal.ONE, "历史空工单"));
        assertThat(flow.workspaces().production().workOrders()).singleElement()
                .satisfies(order -> assertThat(order.operationContext()).isNull());
        assertThat(flow.workspaces().inventory().balances()).hasSize(2)
                .allSatisfy(balance -> assertThat(balance.operationContext()).isNotNull());
    }

    private Flow flow(boolean jdbc) throws SQLException {
        var documents = new InMemoryBusinessDocumentRepository();
        var stocks = new StockLedgerService(StockRepositoryFixture.create(jdbc));
        var operations = new BusinessOperationService(stocks, null, documents);
        var splits = new MotherOrderSplitService(documents, stocks);
        var parser = new ScanCodeParser();
        var stations = new ProcessStationService(documents, stocks, operations, parser);
        var quality = new QualityRequirementService(documents, stocks, operations, parser);
        var workspaces = new BusinessWorkspaceService(null, documents, new ProgressBoardService(documents), stocks, null, false);
        operations.confirmInbound(new ConfirmInboundCommand(MOTHER, 7001L, 7001L, "MAT-REAL", "2026-10-10", "", "", "",
                "收货员", "收货员", "戒托", "", null, "", RAW, bd("2"), "件", bd("12.5"), "收货员", "", "",
                "无划痕", "2099-01-01", "真实客户"));
        return new Flow(stocks, operations, splits, stations, quality, workspaces);
    }

    private void finish(Flow flow) {
        for (String process : WorkshopProcesses.DEFAULT_ORDER) flow.stations().report(MOTHER, process, worker(process));
    }

    private void assertContext(OperationContext context, String workOrder, String warehouse, StockStatus status) {
        assertThat(context).isNotNull();
        assertThat(context.customerId()).isEqualTo(7001L);
        assertThat(context.ownerCustomerId()).isEqualTo(7001L);
        assertThat(context.motherOrderCode()).isEqualTo(MOTHER);
        assertThat(context.workOrderCode()).isEqualTo(workOrder);
        assertThat(context.materialCode()).isEqualTo("MAT-REAL");
        assertThat(context.warehouseCode()).isEqualTo(warehouse);
        assertThat(context.stockStatus()).isEqualTo(status.name());
        assertThat(context.quantity()).isEqualByComparingTo("2");
        assertThat(context.weight()).isEqualByComparingTo("12.5");
    }

    private AuthUserAccount worker(String process) {
        return new AuthUserAccount(process, "hash", process + "工", RoleCode.OPERATOR, DataScope.SELF, true, true)
                .withAllowedProcesses(List.of(process));
    }

    private AuthUserAccount inspector() {
        return new AuthUserAccount("qc", "hash", "质检员", RoleCode.QUALITY_INSPECTOR, DataScope.DEPARTMENT, true, true);
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }

    private record Flow(StockLedgerService stocks, BusinessOperationService operations, MotherOrderSplitService splits,
                        ProcessStationService stations, QualityRequirementService quality, BusinessWorkspaceService workspaces) {
    }
}
