package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.OutsourcingCounterpart;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingReturnBalance;
import com.zhubao.erp.business.domain.OutsourcingReturnRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingSourceRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.business.infra.JdbcOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.JdbcOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.JdbcOutsourcingSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutsourcingReturnTest {

    @Test
    void 发200收150损5仍外45且少回不自动清结() {
        Fixture fixture = memory();
        Sent closed = send(fixture, "BAG-R03", "MD-20261030-0301", new BigDecimal("200"));
        OutsourcingReturn row = fixture.returns().receive(
                "d03-ret-01", "clerk-01", closed.dispatch().dispatchId(), closed.dispatch().version(),
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"),
                StockMeasurement.known(new BigDecimal("2.250")));
        OutsourcingReturn replay = fixture.returns().receive(
                "d03-ret-01", "clerk-01", closed.dispatch().dispatchId(), closed.dispatch().version(),
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"),
                StockMeasurement.known(new BigDecimal("2.250")));
        assertThat(replay.returnId()).isEqualTo(row.returnId());
        assertThat(fixture.returnRows().listByDispatch(closed.dispatch().dispatchId())).hasSize(1);
        OutsourcingReturnBalance balance = fixture.returns().balance(closed.dispatch().dispatchId());
        assertThat(balance.dispatched()).isEqualByComparingTo("200");
        assertThat(balance.received()).isEqualByComparingTo("150");
        assertThat(balance.unprocessed()).isEqualByComparingTo("0");
        assertThat(balance.confirmedLoss()).isEqualByComparingTo("5");
        assertThat(balance.stillOut()).isEqualByComparingTo("45");
        assertThat(balance.received().add(balance.confirmedLoss()).add(balance.stillOut())).isEqualByComparingTo("200");
        assertThat(balance.pending()).isTrue();
        assertThat(balance.settled()).isFalse();
        assertThat(row.weight().value()).isEqualByComparingTo("2.250");
        assertThat(fixture.dispatchService().entrustedQuantity(closed.task().taskId())).isEqualByComparingTo("45");
        assertThat(fixture.dispatchRows().find(closed.dispatch().dispatchId()).orElseThrow().quantity()).isEqualByComparingTo("200");
        assertThat(fixture.dispatchRows().find(closed.dispatch().dispatchId()).orElseThrow().ownerType()).isEqualTo(OwnerType.CUSTOMER);

        ActivityBag received = fixture.bags().find(row.receivedBagId()).orElseThrow();
        ActivityBag remainder = fixture.bags().find(row.remainderBagId()).orElseThrow();
        ActivityBag loss = fixture.bags().find(row.lossBagId()).orElseThrow();
        assertThat(received.quantity()).isEqualByComparingTo("150");
        assertThat(received.sourceKey()).isEqualTo("LINE-1");
        assertThat(received.weight().value()).isNull();
        assertThat(fixture.dispatchService().holderOf(received.id())).isEqualTo(OutsourcingDispatchService.IN_HOUSE);
        assertThat(remainder.quantity()).isEqualByComparingTo("45");
        assertThat(remainder.sourceKey()).isEqualTo("LINE-1");
        assertThat(remainder.weight().value()).isNull();
        assertThat(fixture.dispatchService().holderOf(remainder.id())).isEqualTo("WX-POLISH-01");
        assertThat(loss.closed()).isTrue();
        assertThat(loss.quantity()).isEqualByComparingTo("5");
        assertThat(loss.weight().value()).isNull();
        assertThat(fixture.sources().findTask(closed.task().taskId()).orElseThrow().nodeId()).isEqualTo("POLISH_BEAD");

        assertThatThrownBy(() -> fixture.returns().receive(
                "d03-stale1", "clerk-01", closed.dispatch().dispatchId(), 1,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协发外已被其他人修改，请刷新后重试");
        assertThatThrownBy(() -> fixture.returns().receive(
                "d03-over01", "clerk-01", closed.dispatch().dispatchId(), row.dispatchVersion(),
                new BigDecimal("46"), BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能超收");
        assertThat(fixture.returns().balance(closed.dispatch().dispatchId()).stillOut()).isEqualByComparingTo("45");

        Sent shortSend = send(fixture, "BAG-SHORT", "MD-20261030-0302", new BigDecimal("200"));
        fixture.returns().receive("d03-short1", "clerk-01", shortSend.dispatch().dispatchId(), 1,
                new BigDecimal("150"), BigDecimal.ZERO, BigDecimal.ZERO, null);
        OutsourcingReturnBalance shortBalance = fixture.returns().balance(shortSend.dispatch().dispatchId());
        assertThat(shortBalance.confirmedLoss()).isEqualByComparingTo("0");
        assertThat(shortBalance.stillOut()).isEqualByComparingTo("50");
        assertThat(shortBalance.settled()).isFalse();
        assertThat(shortBalance.pending()).isTrue();
        OutsourcingReturn shortRow = fixture.returnRows().listByDispatch(shortSend.dispatch().dispatchId()).get(0);
        assertThat(shortRow.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(shortRow.weight().value()).isNull();
    }

    @Test
    void 未加工退回回到本厂且不写成完工() {
        Fixture fixture = memory();
        Sent sent = send(fixture, "BAG-BACK", "MD-20261030-0303", new BigDecimal("200"));
        OutsourcingReturn row = fixture.returns().receive(
                "d03-raw-01", "clerk-01", sent.dispatch().dispatchId(), 1,
                BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, null);
        OutsourcingReturnBalance balance = fixture.returns().balance(sent.dispatch().dispatchId());
        assertThat(balance.received()).isEqualByComparingTo("0");
        assertThat(balance.unprocessed()).isEqualByComparingTo("20");
        assertThat(balance.confirmedLoss()).isEqualByComparingTo("0");
        assertThat(balance.stillOut()).isEqualByComparingTo("180");
        assertThat(balance.settled()).isFalse();
        ActivityBag back = fixture.bags().find(row.unprocessedBagId()).orElseThrow();
        assertThat(back.activeLeaf()).isTrue();
        assertThat(back.quantity()).isEqualByComparingTo("20");
        assertThat(back.sourceKey()).isEqualTo("LINE-1");
        assertThat(back.weight().value()).isNull();
        assertThat(fixture.dispatchService().holderOf(back.id())).isEqualTo(OutsourcingDispatchService.IN_HOUSE);
        assertThat(fixture.dispatchService().holderOf(row.remainderBagId())).isEqualTo("WX-POLISH-01");
        assertThat(fixture.sources().findTask(sent.task().taskId()).orElseThrow().nodeId()).isEqualTo("POLISH_BEAD");
        assertThat(row.receivedBagId()).isEmpty();
        assertThat(row.lossBagId()).isEmpty();
    }

    @Test
    void jdbc回放并发和失败不重复收回() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcActivityBagRepository bags = new JdbcActivityBagRepository(dataSource);
        JdbcOutsourcingSourceRepository sources = new JdbcOutsourcingSourceRepository(dataSource);
        JdbcOutsourcingDispatchRepository dispatchRows = new JdbcOutsourcingDispatchRepository(dataSource);
        JdbcOutsourcingReturnRepository returnRows = new JdbcOutsourcingReturnRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        Fixture fixture = new Fixture(bags, sources, dispatchRows, returnRows, unit);
        Sent sent = send(fixture, "BAG-J03", "MD-20261030-0304", new BigDecimal("200"));
        OutsourcingReturn first = fixture.returns().receive(
                "d03-jdbc01", "clerk-01", sent.dispatch().dispatchId(), 1,
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"), null);
        OutsourcingReturn replay = fixture.returns().receive(
                "d03-jdbc01", "clerk-01", sent.dispatch().dispatchId(), 1,
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"), null);
        assertThat(replay.returnId()).isEqualTo(first.returnId());
        OutsourcingReturn reloaded = returnRows.find(first.returnId()).orElseThrow();
        assertThat(reloaded.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(reloaded.weight().value()).isNull();
        assertThat(fixture.returns().balance(sent.dispatch().dispatchId()).stillOut()).isEqualByComparingTo("45");
        assertThat(returnRows.listByDispatch(sent.dispatch().dispatchId())).hasSize(1);

        Sent race = send(fixture, "BAG-JRC", "MD-20261030-0305", new BigDecimal("200"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> left = pool.submit(() -> attempt(fixture, race, "d03-race01", start));
            Future<Object> right = pool.submit(() -> attempt(fixture, race, "d03-race02", start));
            start.countDown();
            Object one = left.get(10, TimeUnit.SECONDS);
            Object other = right.get(10, TimeUnit.SECONDS);
            assertThat(List.of(one, other).stream().filter(OutsourcingReturn.class::isInstance).count()).isEqualTo(1);
            assertThat(List.of(one, other)).anySatisfy(item -> assertThat(item).isInstanceOf(BusinessException.class));
        } finally {
            pool.shutdownNow();
        }
        assertThat(fixture.returns().balance(race.dispatch().dispatchId()).stillOut()).isEqualByComparingTo("45");
        assertThat(returnRows.listByDispatch(race.dispatch().dispatchId())).hasSize(1);

        Sent rollback = send(fixture, "BAG-JRB", "MD-20261030-0306", new BigDecimal("30"));
        OutsourcingReturnService failing = new OutsourcingReturnService(
                dispatchRows, returnRows, sources, bags,
                new BagSplitService(bags, new ActivityBagService(bags), unit),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> failing.receive(
                "d03-jfail1", "clerk-01", rollback.dispatch().dispatchId(), 1,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(bags.find(rollback.bag().id()).orElseThrow().closed()).isFalse();
        assertThat(bags.find(rollback.bag().id()).orElseThrow().quantity()).isEqualByComparingTo("30");
        assertThat(returnRows.listByDispatch(rollback.dispatch().dispatchId())).isEmpty();
        assertThat(dispatchRows.find(rollback.dispatch().dispatchId()).orElseThrow().version()).isEqualTo(1);
    }

    private static Object attempt(Fixture fixture, Sent sent, String requestId, CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("并发启动超时");
            }
            return fixture.returns().receive(requestId, "clerk-01", sent.dispatch().dispatchId(), 1,
                    new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"), null);
        } catch (RuntimeException ex) {
            return ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ex;
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void 确认损耗只扣原库存数量且失败回滚(boolean explicitTarget) {
        InMemoryStockLedgerRepository ledger = new InMemoryStockLedgerRepository();
        StockLedgerService stock = new StockLedgerService(ledger);
        Fixture fixture = memory();
        StockDimension source = new StockDimension(
                OwnerType.CUSTOMER, 10086L, 10086L, "MD-20261030-0311", explicitTarget ? "MD-20261030-0311" : "", "牙骨", "WH", StockStatus.WIP);
        stock.transfer(new StockTransferCommand(
                "inbound_confirm", "RF11-IN", null, source, new BigDecimal("200"), new BigDecimal("7.260"), "原收货"));
        OutsourcingReturnService returns = new OutsourcingReturnService(
                fixture.dispatchRows(), fixture.returnRows(), fixture.sources(), fixture.bags(),
                new BagSplitService(fixture.bags(), new ActivityBagService(fixture.bags()), fixture.unit()),
                fixture.unit(), stock);
        Sent sent = send(fixture, "BAG-RF11", "MD-20261030-0311", new BigDecimal("200"));
        OutsourcingReturn row = returns.receive(
                "rf11-ret-01", "clerk-01", sent.dispatch().dispatchId(), 1,
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"),
                StockMeasurement.known(new BigDecimal("2.250")));
        returns.receive(
                "rf11-ret-01", "clerk-01", sent.dispatch().dispatchId(), 1,
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"),
                StockMeasurement.known(new BigDecimal("2.250")));
        StockBalance after = ledger.findBalance(source);
        assertThat(after.quantity()).isEqualByComparingTo("195");
        assertThat(after.weight()).isEqualByComparingTo("7.260");
        assertThat(after.weightPending()).isTrue();
        assertThat(ledger.findAllLedgers().stream().filter(entry -> "outsourcing_loss".equals(entry.documentType())))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.quantity()).isEqualByComparingTo("5");
                    assertThat(entry.weight()).isNull();
                    assertThat(entry.from().ownerType()).isEqualTo(OwnerType.CUSTOMER);
                });
        assertThat(fixture.bags().find(row.receivedBagId()).orElseThrow().quantity()).isEqualByComparingTo("150");
        assertThat(fixture.bags().find(row.remainderBagId()).orElseThrow().quantity()).isEqualByComparingTo("45");
        assertThat(fixture.bags().find(row.lossBagId()).orElseThrow().closed()).isTrue();
        assertThat(fixture.returns().balance(sent.dispatch().dispatchId()).stillOut()).isEqualByComparingTo("45");

        MemoryBusinessWorkUnit failingUnit = new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        });
        OutsourcingReturnService failing = new OutsourcingReturnService(
                fixture.dispatchRows(), fixture.returnRows(), fixture.sources(), fixture.bags(),
                new BagSplitService(fixture.bags(), new ActivityBagService(fixture.bags()), failingUnit),
                failingUnit, stock);
        Sent other = send(fixture, "BAG-RF11B", "MD-20261030-0311", new BigDecimal("20"));
        assertThatThrownBy(() -> failing.receive(
                "rf11-fail01", "clerk-01", other.dispatch().dispatchId(), 1,
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5"), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger.findBalance(source).quantity()).isEqualByComparingTo("195");
        assertThat(ledger.findBalance(source).weight()).isEqualByComparingTo("7.260");
        assertThat(fixture.returnRows().listByDispatch(other.dispatch().dispatchId())).isEmpty();
    }

    @Test
    void 母单外协损耗不能借子单或已发库存扣减() {
        for (String scenario : List.of("CHILD", "SHIPPED", "UNKNOWN_OWNER")) {
            boolean shipped = "SHIPPED".equals(scenario);
            boolean unknownOwner = "UNKNOWN_OWNER".equals(scenario);
            Fixture fixture = memory();
            InMemoryStockLedgerRepository ledger = new InMemoryStockLedgerRepository();
            StockLedgerService stock = new StockLedgerService(ledger);
            StockDimension unrelated = new StockDimension(OwnerType.CUSTOMER, 10086L, 10086L,
                    "MD-20261030-0311", shipped || unknownOwner ? "" : "ZD-20261030-9999", "牙骨", "WH",
                    shipped ? StockStatus.SHIPPED : StockStatus.WIP);
            stock.transfer(new StockTransferCommand("inbound_confirm", "SOURCE-RF11", null, unrelated,
                    new BigDecimal("10"), BigDecimal.ONE, "其他来源"));
            Sent sent = send(fixture, "BAG-SOURCE", "MD-20261030-0311", new BigDecimal("10"), unknownOwner ? "客户甲" : "10086");
            OutsourcingReturnService service = new OutsourcingReturnService(
                    fixture.dispatchRows(), fixture.returnRows(), fixture.sources(), fixture.bags(),
                    new BagSplitService(fixture.bags(), new ActivityBagService(fixture.bags()), fixture.unit()), fixture.unit(), stock);
            assertThatThrownBy(() -> service.receive("rf11-wrong-source", "clerk-01", sent.dispatch().dispatchId(), 1,
                    new BigDecimal("8"), BigDecimal.ZERO, new BigDecimal("2"), StockMeasurement.known(new BigDecimal("0.8"))))
                    .isInstanceOf(BusinessException.class).hasMessage(unknownOwner ? "无法确认损耗的原物料权属" : "无法确认损耗的原库存");
            assertThat(ledger.findBalance(unrelated).quantity()).isEqualByComparingTo("10");
            assertThat(fixture.returnRows().listByDispatch(sent.dispatch().dispatchId())).isEmpty();
            assertThat(fixture.bags().find(sent.dispatch().sentBagId()).orElseThrow().closed()).isFalse();
        }
    }

    @Test
    void jdbc收回损耗与库存和重量待核使用同一事务() throws Exception {
        JdbcDataSource ds = dataSource();
        var unit = new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds));
        Fixture fixture = new Fixture(new JdbcActivityBagRepository(ds), new JdbcOutsourcingSourceRepository(ds),
                new JdbcOutsourcingDispatchRepository(ds), new JdbcOutsourcingReturnRepository(ds), unit);
        var ledger = new com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository(ds);
        var stock = new StockLedgerService(ledger);
        StockDimension source = new StockDimension(OwnerType.CUSTOMER, 10086L, 10086L,
                "MD-20261030-0311", "", "牙骨", "WH", StockStatus.WIP);
        stock.transfer(new StockTransferCommand("inbound_confirm", "JDBC-LOSS-IN", null, source,
                new BigDecimal("200"), new BigDecimal("7.260"), "原收货"));
        Sent sent = send(fixture, "BAG-LOSS-JDBC", "MD-20261030-0311", new BigDecimal("200"));
        var service = new OutsourcingReturnService(fixture.dispatchRows(), fixture.returnRows(), fixture.sources(), fixture.bags(),
                new BagSplitService(fixture.bags(), new ActivityBagService(fixture.bags()), unit), unit, stock);
        service.receive("rf11-jdbc-return", "clerk-01", sent.dispatch().dispatchId(), 1,
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"), StockMeasurement.known(new BigDecimal("2.250")));
        assertThat(ledger.findBalance(source).quantity()).isEqualByComparingTo("195");
        assertThat(ledger.findBalance(source).weightPending()).isTrue();
        StockDimension other = new StockDimension(OwnerType.CUSTOMER, 10086L, 10086L,
                "MD-20261030-0312", "", "牙骨", "WH", StockStatus.WIP);
        stock.transfer(new StockTransferCommand("inbound_confirm", "JDBC-FAIL-IN", null, other,
                new BigDecimal("20"), BigDecimal.ONE, "另一单原收货"));
        Sent failingSent = send(fixture, "BAG-LOSS-FAIL", "MD-20261030-0312", new BigDecimal("20"));
        var failing = new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds), () -> { throw new IllegalStateException("失败"); });
        var failedService = new OutsourcingReturnService(fixture.dispatchRows(), fixture.returnRows(), fixture.sources(), fixture.bags(),
                new BagSplitService(fixture.bags(), new ActivityBagService(fixture.bags()), failing), failing, stock);
        assertThatThrownBy(() -> failedService.receive("rf11-jdbc-failed", "clerk-01", failingSent.dispatch().dispatchId(), 1,
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("5"), null)).isInstanceOf(IllegalStateException.class);
        assertThat(ledger.findBalance(other).quantity()).isEqualByComparingTo("20");
        assertThat(ledger.findBalance(other).weightPending()).isFalse();
        assertThat(fixture.returnRows().listByDispatch(failingSent.dispatch().dispatchId())).isEmpty();
        assertThat(fixture.bags().find(failingSent.bag().id()).orElseThrow().closed()).isFalse();
    }

    private static Fixture memory() {
        return new Fixture(new InMemoryActivityBagRepository(), new InMemoryOutsourcingSourceRepository(),
                new InMemoryOutsourcingDispatchRepository(), new InMemoryOutsourcingReturnRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
    }

    private static Sent send(Fixture fixture, String bagId, String target, BigDecimal quantity) {
        return send(fixture, bagId, target, quantity, "10086");
    }

    private static Sent send(Fixture fixture, String bagId, String target, BigDecimal quantity, String ownerRef) {
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        routes.insert(new RouteSnapshot("RS-" + target, BusinessKind.PROCESSING, target, 1,
                ProcessCatalog.defaults(BusinessKind.PROCESSING, "牙骨").stream().map(ProcessStep::id).toList(), true), null);
        ActivityBag bag = new ActivityBag(bagId, target, "牙骨", "REQ-1", "LINE-1", "", false,
                quantity, StockMeasurement.unknown(), 1);
        fixture.unit().execute(new CommandCall("bag-" + bagId, "clerk-01", "SAVE_BAG", "BAG", bagId, bagId), session -> {
            fixture.bags().save(bag, 0, session);
            return bag.id();
        });
        OutsourcingSourceService sources = new OutsourcingSourceService(fixture.sources(), fixture.bags(), routes, fixture.unit());
        if (fixture.sources().findParty("WX-POLISH-01").isEmpty()) {
            sources.register("reg-" + bagId, OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "甲抛光", "clerk-01");
        }
        OutsourcingTask task = sources.openTask("task-" + bagId, "WX-POLISH-01", target, "POLISH_BEAD",
                bagId, OwnerType.CUSTOMER, ownerRef, "clerk-01");
        OutsourcingDispatch dispatch = fixture.dispatchService().dispatch(
                "dsp-" + bagId, "clerk-01", task.taskId(), bagId, 1, quantity);
        return new Sent(bag, task, dispatch);
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = com.zhubao.erp.inventory.support.StockRepositoryFixture.jdbcDataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V81__m3_activity_bag.sql",
                    "V88__m3_outsourcing_source.sql", "V89__m3_outsourcing_dispatch.sql",
                    "V90__m3_outsourcing_return.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }

    private record Sent(ActivityBag bag, OutsourcingTask task, OutsourcingDispatch dispatch) {
    }

    private record Fixture(ActivityBagRepository bags,
                           com.zhubao.erp.business.domain.OutsourcingSourceRepository sources,
                           com.zhubao.erp.business.domain.OutsourcingDispatchRepository dispatchRows,
                           OutsourcingReturnRepository returnRows,
                           BusinessWorkUnit unit) {
        OutsourcingDispatchService dispatchService() {
            return new OutsourcingDispatchService(sources, dispatchRows, returnRows, bags,
                    new BagSplitService(bags, new ActivityBagService(bags), unit), unit);
        }

        OutsourcingReturnService returns() {
            return new OutsourcingReturnService(dispatchRows, returnRows, sources, bags,
                    new BagSplitService(bags, new ActivityBagService(bags), unit), unit);
        }
    }
}
