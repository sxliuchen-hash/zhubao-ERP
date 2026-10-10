package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正式收货、拆单及工序路径共用同一套行为断言，保证内存与 JDBC 的维度契约一致。
 */
class FormalStockFlowContractTest {

    @ParameterizedTest(name = "正式拆单及全部工序，JDBC={0}")
    @ValueSource(booleans = {false, true})
    void splitChildrenPreserveSourceDimensionsThroughEveryStation(boolean jdbc) throws SQLException {
        Flow flow = flow(jdbc);
        String mother = "MD-CONTRACT-SPLIT";
        inbound(flow, mother);
        assertInbound(flow, mother);
        var split = flow.splits().split(new SplitMotherOrderCommand(mother, "何琳", List.of(
                new SplitMotherOrderLine(bd("4"), bd("8"), null),
                new SplitMotherOrderLine(bd("6"), bd("12"), null)
        )));

        assertBalance(flow, dimension(mother, null, StockStatus.RAW_WAITING), "0", "0");
        for (var child : split.children()) {
            assertBalance(flow, dimension(mother, child.code(), StockStatus.WIP),
                    child.quantity().toPlainString(), child.weight().toPlainString());
            deliverAllStations(flow, mother, child.code(), child.quantity(), child.weight());
        }

        List<StockLedgerEntry> reloaded = flow.reloaded().findAllLedgers();
        assertThat(reloaded).hasSize(9);
        assertThat(reloaded).filteredOn(entry -> "work_order_split".equals(entry.documentType()))
                .hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.from()).isEqualTo(dimension(mother, null, StockStatus.RAW_WAITING));
                    assertThat(entry.to()).isEqualTo(dimension(mother, entry.documentCode(), StockStatus.WIP));
                });
        assertStationDimensions(reloaded, mother);
    }

    @ParameterizedTest(name = "不拆母单首报及全部工序，JDBC={0}")
    @ValueSource(booleans = {false, true})
    void keptMotherPreservesNullSourceWorkOrderAtFirstReport(boolean jdbc) throws SQLException {
        Flow flow = flow(jdbc);
        String mother = "MD-CONTRACT-KEEP";
        inbound(flow, mother);
        assertInbound(flow, mother);
        flow.splits().keep(mother, "何琳");
        assertThat(flow.stocks().ledgers()).hasSize(1);
        deliverAllStations(flow, mother, mother, bd("10"), bd("20"));

        List<StockLedgerEntry> reloaded = flow.reloaded().findAllLedgers();
        assertThat(reloaded).hasSize(5);
        assertThat(reloaded).filteredOn(entry -> "work_order_split".equals(entry.documentType()))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.from()).isEqualTo(dimension(mother, null, StockStatus.RAW_WAITING));
                    assertThat(entry.to()).isEqualTo(dimension(mother, mother, StockStatus.WIP));
                });
        assertStationDimensions(reloaded, mother);
        assertBalance(flow, dimension(mother, null, StockStatus.RAW_WAITING), "0", "0");
    }

    private void deliverAllStations(Flow flow, String mother, String order, BigDecimal quantity, BigDecimal weight) {
        for (String process : List.of("雕刻", "镶嵌", "抛光")) {
            var result = flow.stations().report(order, process, account(process));
            boolean last = "抛光".equals(process);
            assertThat(result.movedToSemiFinished()).isEqualTo(last);
            assertBalance(flow, dimension(mother, order, StockStatus.WIP),
                    last ? "0" : quantity.toPlainString(), last ? "0" : weight.toPlainString());
            assertBalance(flow, dimension(mother, order, StockStatus.SEMI_FINISHED),
                    last ? quantity.toPlainString() : "0", last ? weight.toPlainString() : "0");
        }
    }

    private void assertStationDimensions(List<StockLedgerEntry> ledgers, String mother) {
        assertThat(ledgers).filteredOn(entry -> "process_station_report".equals(entry.documentType()))
                .allSatisfy(entry -> {
                    assertThat(entry.from()).isEqualTo(dimension(mother, entry.documentCode(), StockStatus.WIP));
                    assertThat(entry.to()).isEqualTo(entry.from());
                });
        assertThat(ledgers).filteredOn(entry -> "production_report".equals(entry.documentType()))
                .allSatisfy(entry -> {
                    assertThat(entry.from()).isEqualTo(dimension(mother, entry.documentCode(), StockStatus.WIP));
                    assertThat(entry.to()).isEqualTo(dimension(mother, entry.documentCode(), StockStatus.SEMI_FINISHED));
                });
    }

    private void assertInbound(Flow flow, String mother) {
        assertBalance(flow, dimension(mother, null, StockStatus.RAW_WAITING), "10", "20");
        assertThat(flow.reloaded().findAllLedgers()).singleElement().satisfies(entry -> {
            assertThat(entry.from()).isNull();
            assertThat(entry.to()).isEqualTo(dimension(mother, null, StockStatus.RAW_WAITING));
        });
    }

    private void assertBalance(Flow flow, StockDimension dimension, String quantity, String weight) {
        var balance = flow.reloaded().findBalance(dimension);
        assertThat(balance.quantity()).isEqualByComparingTo(quantity);
        assertThat(balance.weight()).isEqualByComparingTo(weight);
    }

    private Flow flow(boolean jdbc) throws SQLException {
        JdbcDataSource dataSource = jdbc ? StockRepositoryFixture.jdbcDataSource() : null;
        StockLedgerRepository repository = jdbc ? new JdbcStockLedgerRepository(dataSource) : new InMemoryStockLedgerRepository();
        StockLedgerService stocks = new StockLedgerService(repository);
        var documents = new InMemoryBusinessDocumentRepository();
        var operations = new BusinessOperationService(stocks, null, documents);
        return new Flow(stocks, operations, new MotherOrderSplitService(documents, stocks),
                new ProcessStationService(documents, stocks, operations, new ScanCodeParser()),
                jdbc ? new JdbcStockLedgerRepository(dataSource) : repository);
    }

    private void inbound(Flow flow, String mother) {
        flow.operations().confirmInbound(new ConfirmInboundCommand(
                mother, 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF" + mother,
                "", "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                bd("10"), "件", bd("20"), "何琳", "", "测试入库", "内壁刻字", "2026-10-20", "恒信珠宝"
        ));
    }

    private StockDimension dimension(String mother, String workOrder, StockStatus status) {
        return new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, mother, workOrder,
                "AU9999", "RAW-CUSTOMER", status);
    }

    private AuthUserAccount account(String process) {
        return new AuthUserAccount("工序员" + process, "hash", "工序员" + process, RoleCode.OPERATOR,
                DataScope.SELF, true, true).withAllowedProcesses(List.of(process));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record Flow(StockLedgerService stocks, BusinessOperationService operations,
                        MotherOrderSplitService splits, ProcessStationService stations,
                        StockLedgerRepository reloaded) {
    }
}
