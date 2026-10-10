package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** 内存和 JDBC 必须共同遵守的库存原子写入契约。 */
class StockRepositoryContractTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentDebitsAllowOnlyOneWhenTenUnitsAreDebitedBySixTwice(boolean jdbc) throws Exception {
        Fixture fixture = fixture(jdbc);
        StockDimension raw = dimension(StockStatus.WIP, "WIP");
        StockDimension target = dimension(StockStatus.SEMI_FINISHED, "SEMI");
        fixture.repository.save(entry("IN", null, raw, "10", "10"));
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = executor.invokeAll(List.of(
                    () -> debit(fixture.repository, start, "A", raw, target),
                    () -> debit(fixture.reload(), start, "B", raw, target)));
            assertThat(List.of(attempts.get(0).get(), attempts.get(1).get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        StockLedgerRepository reloaded = fixture.reload();
        assertThat(reloaded.findBalance(raw).quantity()).isEqualByComparingTo("4");
        assertThat(reloaded.findBalance(target).quantity()).isEqualByComparingTo("6");
        assertThat(reloaded.findAllLedgers()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentCreditsToAnInitiallyMissingTargetAccumulate(boolean jdbc) throws Exception {
        Fixture fixture = fixture(jdbc);
        StockDimension target = dimension(StockStatus.RAW_WAITING, "RAW");
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<StockLedgerEntry>> attempts = executor.invokeAll(List.of(
                    () -> { start.await(5, TimeUnit.SECONDS); return fixture.repository.save(entry("A", null, target, "6", "6")); },
                    () -> { start.await(5, TimeUnit.SECONDS); return fixture.reload().save(entry("B", null, target, "6", "6")); }));
            for (Future<StockLedgerEntry> attempt : attempts) attempt.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(fixture.reload().findBalance(target).quantity()).isEqualByComparingTo("12");
        assertThat(fixture.reload().findAllLedgers()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void insufficientWeightAndDuplicateLedgerLeaveNoPartialChanges(boolean jdbc) throws Exception {
        Fixture fixture = fixture(jdbc);
        StockDimension raw = dimension(StockStatus.WIP, "WIP");
        StockDimension target = dimension(StockStatus.SEMI_FINISHED, "SEMI");
        fixture.repository.save(entry("IN", null, raw, "10", "5"));
        assertThatThrownBy(() -> fixture.repository.save(entry("BAD", raw, target, "6", "6")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("重量不足");
        assertThatThrownBy(() -> fixture.repository.save(entry("IN", raw, target, "2", "2")))
                .isInstanceOf(RuntimeException.class);
        StockLedgerRepository reloaded = fixture.reload();
        assertThat(reloaded.findBalance(raw).quantity()).isEqualByComparingTo("10");
        assertThat(reloaded.findBalance(raw).weight()).isEqualByComparingTo("5");
        assertThat(reloaded.findBalance(target).quantity()).isEqualByComparingTo("0");
        assertThat(reloaded.findAllLedgers()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void destinationPersistenceFailureRollsBackDebitAndLedger(boolean jdbc) throws Exception {
        Fixture fixture = fixture(jdbc);
        StockDimension raw = dimension(StockStatus.WIP, "WIP");
        StockDimension invalid = dimension(StockStatus.SEMI_FINISHED, null);
        fixture.repository.save(entry("IN", null, raw, "10", "10"));
        assertThatThrownBy(() -> fixture.repository.save(entry("FAIL", raw, invalid, "6", "6")))
                .isInstanceOf(RuntimeException.class);
        assertThat(fixture.reload().findBalance(raw).quantity()).isEqualByComparingTo("10");
        assertThat(fixture.reload().findAllLedgers()).hasSize(1);
    }

    @Test
    void jdbcFailureAfterSourceDebitRollsBackEveryWrite() throws Exception {
        Fixture fixture = fixture(true);
        StockDimension raw = dimension(StockStatus.WIP, "WIP");
        StockDimension target = dimension(StockStatus.SEMI_FINISHED, "SEMI");
        fixture.repository.save(entry("IN", null, raw, "10", "10"));
        fixture.repository.save(entry("TARGET", null, target, "10", "10"));
        // 目标超过上限时数据库拒绝增量，故障发生在来源扣减和台账插入之后。
        try (var connection = fixture.source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("alter table inv_stock_balance add constraint test_capacity check (available_quantity <= 10)");
        }
        assertThatThrownBy(() -> fixture.repository.save(entry("FAIL", raw, target, "1", "1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.reload().findBalance(raw).quantity()).isEqualByComparingTo("10");
        assertThat(fixture.reload().findBalance(target).quantity()).isEqualByComparingTo("10");
        assertThat(fixture.reload().findAllLedgers()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameDimensionStationEntryChecksBalanceAndHasZeroNetChange(boolean jdbc) throws Exception {
        Fixture fixture = fixture(jdbc);
        StockDimension wip = dimension(StockStatus.WIP, "WIP");
        StockLedgerService service = new StockLedgerService(fixture.repository);
        service.transfer(new StockTransferCommand("inbound", "MD", null, wip, bd("10"), bd("10"), null));
        service.transfer(new StockTransferCommand("process_station_report", "ZD", wip, wip, bd("6"), bd("6"), null));
        assertThatThrownBy(() -> service.transfer(new StockTransferCommand("process_station_report", "ZD", wip, wip, bd("11"), bd("11"), null)))
                .isInstanceOf(BusinessException.class);
        assertThat(fixture.reload().findBalance(wip).quantity()).isEqualByComparingTo("10");
        assertThat(fixture.reload().findAllLedgers()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsArbitraryOrderCustomerAndMaterialChanges(boolean jdbc) throws Exception {
        Fixture fixture = fixture(jdbc);
        StockDimension raw = dimension(StockStatus.WIP, "WIP");
        fixture.repository.save(entry("IN", null, raw, "10", "10"));
        List<StockDimension> invalidTargets = List.of(
                new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, "MD", "OTHER", "GOLD", "SEMI", StockStatus.SEMI_FINISHED),
                new StockDimension(OwnerType.CUSTOMER, 1001L, 1002L, "MD", "ZD", "GOLD", "SEMI", StockStatus.SEMI_FINISHED),
                new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, "MD", "ZD", "SILVER", "SEMI", StockStatus.SEMI_FINISHED));
        for (StockDimension target : invalidTargets) {
            assertThatThrownBy(() -> fixture.repository.save(entry("INVALID", raw, target, "1", "1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(fixture.reload().findBalance(raw).quantity()).isEqualByComparingTo("10");
        assertThat(fixture.reload().findAllLedgers()).hasSize(1);
    }

    private boolean debit(StockLedgerRepository repository, CyclicBarrier start, String code,
                          StockDimension from, StockDimension to) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            repository.save(entry(code, from, to, "6", "6"));
            return true;
        } catch (BusinessException ex) {
            assertThat(ex.getMessage()).contains("不足");
            return false;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsRoundingAndEmptyTransfersWithoutChangingStock(boolean jdbc) throws Exception {
        Fixture fixture = fixture(jdbc);
        StockDimension wip = dimension(StockStatus.WIP, "WIP");
        fixture.repository.save(entry("IN", null, wip, "10", "10"));
        for (String amount : List.of("0.0005", "1000000000000000", "0")) {
            assertThatThrownBy(() -> fixture.repository.save(entry("BAD", wip, wip, amount, amount)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(fixture.reload().findBalance(wip).quantity()).isEqualByComparingTo("10");
        assertThat(fixture.reload().findAllLedgers()).hasSize(1);
    }

    private Fixture fixture(boolean jdbc) throws Exception {
        JdbcDataSource source = jdbc ? StockRepositoryFixture.jdbcDataSource() : null;
        return new Fixture(jdbc ? new JdbcStockLedgerRepository(source) : new InMemoryStockLedgerRepository(), source);
    }

    private StockDimension dimension(StockStatus status, String warehouse) {
        return new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, "MD", "ZD", "GOLD", warehouse, status);
    }

    private StockLedgerEntry entry(String code, StockDimension from, StockDimension to, String quantity, String weight) {
        return new StockLedgerEntry(code, Instant.now(), "test", code, from, to, bd(quantity), bd(weight), null);
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }

    private record Fixture(StockLedgerRepository repository, JdbcDataSource source) {
        StockLedgerRepository reload() { return source == null ? repository : new JdbcStockLedgerRepository(source); }
    }
}
