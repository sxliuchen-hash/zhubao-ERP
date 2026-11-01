package com.zhubao.erp.inventory.app;

import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.*;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.*;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

/** 未称损耗后的重量不确定性与库存事务一起保存，实际称重转移才产生已知去向重量。 */
class StockWeightPendingTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 未称开工流转和实称核正使用同一数量账且回滚恢复两端(boolean jdbc) throws Exception {
        var ds=jdbc ? StockRepositoryFixture.jdbcDataSource() : null;
        if (jdbc) try (var connection=ds.getConnection();var sql=connection.createStatement()) {
            for (String statement:MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql/V44__m3_command_request.sql")))) sql.execute(statement);
        }
        StockLedgerRepository repository=jdbc ? new JdbcStockLedgerRepository(ds) : new InMemoryStockLedgerRepository();
        var stock=new StockLedgerService(repository);
        var raw=new StockDimension(OwnerType.CUSTOMER,7L,7L,"MD-20261102-0001",null,"牙骨","WH",StockStatus.RAW_WAITING);
        var wip=new StockDimension(OwnerType.CUSTOMER,7L,7L,"MD-20261102-0001","MD-20261102-0001","牙骨","WH",StockStatus.WIP);
        stock.transfer(new StockTransferCommand("inbound_confirm","LEGACY-UNKNOWN",null,raw,BigDecimal.TEN,BigDecimal.ZERO,"历史重量尚未核对"));
        BusinessWorkUnit failing=jdbc ? new JdbcBusinessWorkUnit(ds,new JdbcCommandRequestRepository(ds),() -> {throw new IllegalStateException("失败");}) : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(),() -> {throw new IllegalStateException("失败");});
        var call=new CommandCall("unknown-start-01","a","START","MOTHER_ORDER",raw.motherOrderCode(),"10");
        assertThatThrownBy(() -> failing.execute(call,session -> { stock.postUnweighedMovement("production_start","START",raw,wip,BigDecimal.TEN,"重量待核",session); return "done"; })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findBalance(raw).quantity()).isEqualByComparingTo("10"); assertThat(repository.findBalance(wip).quantity()).isEqualByComparingTo("0");
        BusinessWorkUnit unit=jdbc ? new JdbcBusinessWorkUnit(ds,new JdbcCommandRequestRepository(ds)) : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        unit.execute(call,session -> { stock.postUnweighedMovement("production_start","START",raw,wip,BigDecimal.TEN,"重量待核",session); return "done"; });
        assertThat(repository.findBalance(wip).weightPending()).isTrue();
        unit.execute(new CommandCall("unknown-finish","a","FINISH","MOTHER_ORDER",raw.motherOrderCode(),"8"),session -> {
            stock.postUnweighedMovement("process_loss","LOSS",wip,null,new BigDecimal("2"),"实损2颗未称",session);
            stock.transfer(new StockTransferCommand("stock_reweigh","WEIGH",null,wip,BigDecimal.ZERO,new BigDecimal("0.8"),"剩余8颗实称0.8克"),session);
            var finished=new StockDimension(wip.ownerType(),7L,7L,wip.motherOrderCode(),wip.workOrderCode(),"牙骨","WH",StockStatus.FINISHED);
            stock.transfer(new StockTransferCommand("production_finished","BAG",wip,finished,new BigDecimal("8"),new BigDecimal("0.8"),"实称成品"),session);
            return "done";
        });
        StockLedgerRepository reader=jdbc ? new JdbcStockLedgerRepository(ds) : repository;
        assertThat(reader.findAllLedgers()).filteredOn(row -> "production_start".equals(row.documentType()) || "process_loss".equals(row.documentType())).allSatisfy(row -> assertThat(row.weight()).isNull());
        assertThat(reader.findAllBalances()).filteredOn(row -> row.dimension().stockStatus()==StockStatus.FINISHED).singleElement().satisfies(row -> { assertThat(row.quantity()).isEqualByComparingTo("8"); assertThat(row.weight()).isEqualByComparingTo("0.8"); assertThat(row.weightPending()).isFalse(); });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 未称损耗标待复称且失败回滚和重读一致(boolean jdbc) throws Exception {
        var ds = jdbc ? StockRepositoryFixture.jdbcDataSource() : null;
        if (jdbc) {
            try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
                for (String sql : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql/V44__m3_command_request.sql")))) {
                    statement.execute(sql);
                }
            }
        }
        StockLedgerRepository repository = jdbc ? new JdbcStockLedgerRepository(ds) : new InMemoryStockLedgerRepository();
        var stock = new StockLedgerService(repository);
        var source = new StockDimension(OwnerType.CUSTOMER, 7L, 7L, "MD-20261030-1001", "", "牙骨", "WH", StockStatus.WIP);
        stock.transfer(new StockTransferCommand("inbound_confirm", "WEIGHT-IN", null, source, new BigDecimal("200"), new BigDecimal("20"), "原称重"));
        BusinessWorkUnit failing = jdbc
                ? new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds), () -> { throw new IllegalStateException("失败"); })
                : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> { throw new IllegalStateException("失败"); });
        var command = new CommandCall("weight-loss-rf51", "clerk", "OUTSIDE_LOSS", "BAG", "BAG-1", "5");
        assertThatThrownBy(() -> failing.execute(command, session -> {
            stock.postUnweighedLoss("RETURN-1", source, new BigDecimal("5"), session); return "RETURN-1";
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findBalance(source).weightPending()).isFalse();
        assertThat(repository.findBalance(source).quantity()).isEqualByComparingTo("200");
        BusinessWorkUnit unit = jdbc ? new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds))
                : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        unit.execute(command, session -> { stock.postUnweighedLoss("RETURN-1", source, new BigDecimal("5"), session); return "RETURN-1"; });
        StockLedgerRepository reader = jdbc ? new JdbcStockLedgerRepository(ds) : repository;
        var pending = reader.findBalance(source);
        assertThat(pending.weightPending()).isTrue();
        assertThat(pending.quantity()).isEqualByComparingTo("195");
        assertThat(pending.weight()).isEqualByComparingTo("20");
        if (jdbc) {
            // 旧库已有未称损耗时，升级只补待核标记，不改数量和原重量。
            try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
                statement.execute("update inv_stock_balance set weight_pending=false");
                for (String sql : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql/V135__m3_stock_weight_pending.sql")))) {
                    if (sql.stripLeading().startsWith("update")) statement.execute(sql);
                }
            }
            assertThat(reader.findBalance(source).weightPending()).isTrue();
            assertThat(reader.findBalance(source).weight()).isEqualByComparingTo("20");
        }
        assertThat(reader.findAllBalances()).singleElement().satisfies(balance -> assertThat(balance.weightPending()).isTrue());
        var measured = new StockDimension(OwnerType.CUSTOMER, 7L, 7L, "MD-20261030-1001", "", "牙骨", "WH", StockStatus.FINISHED);
        stock.transfer(new StockTransferCommand("quality_release", "ACTUAL-WEIGH", source, measured, new BigDecimal("195"), new BigDecimal("19.5"), "剩余份额实称后放行"));
        assertThat(reader.findBalance(measured).weightPending()).isFalse();
        assertThat(reader.findBalance(measured).weight()).isEqualByComparingTo("19.5");
        var full = new StockDimension(OwnerType.CUSTOMER, 7L, 7L, "MD-20261030-1001", "", "牙骨", "FULL", StockStatus.WIP);
        stock.transfer(new StockTransferCommand("inbound_confirm", "FULL-IN", null, full, BigDecimal.TEN, BigDecimal.ONE, "全损前称重"));
        unit.execute(new CommandCall("weight-full-rf51", "clerk", "OUTSIDE_LOSS", "BAG", "BAG-FULL", "10"), session -> {
            stock.postUnweighedLoss("FULL-RETURN", full, BigDecimal.TEN, session); return "FULL-RETURN";
        });
        assertThat(reader.findBalance(full).quantity()).isEqualByComparingTo("0");
        assertThat(reader.findBalance(full).weightPending()).isTrue();
    }
}
