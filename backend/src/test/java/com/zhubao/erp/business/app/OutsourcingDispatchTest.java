package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.OutsourcingCounterpart;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
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
import com.zhubao.erp.business.infra.JdbcOutsourcingSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
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

class OutsourcingDispatchTest {

    @Test
    void 部分发外先分袋且来源权属不变() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryOutsourcingSourceRepository sources = new InMemoryOutsourcingSourceRepository();
        InMemoryOutsourcingDispatchRepository dispatches = new InMemoryOutsourcingDispatchRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        OutsourcingDispatchService service = service(sources, dispatches, bags, unit);
        Prepared prepared = prepare(sources, bags, unit, "BAG-D02", "MD-20261030-0202",
                new BigDecimal("200"), StockMeasurement.known(new BigDecimal("1.500")));

        OutsourcingDispatch sent = service.dispatch("d02-part01", "clerk-01", prepared.task().taskId(),
                prepared.bag().id(), prepared.bag().version(), new BigDecimal("80"));
        OutsourcingDispatch replay = service.dispatch("d02-part01", "clerk-01", prepared.task().taskId(),
                prepared.bag().id(), prepared.bag().version(), new BigDecimal("80"));
        assertThat(replay.dispatchId()).isEqualTo(sent.dispatchId());
        assertThat(replay.sentBagId()).isEqualTo(sent.sentBagId());
        assertThat(dispatches.listByTask(prepared.task().taskId())).hasSize(1);
        assertThat(service.entrustedQuantity(prepared.task().taskId())).isEqualByComparingTo("80");
        assertThat(service.holderOf(sent.sentBagId())).isEqualTo("WX-POLISH-01");
        assertThat(sent.ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(sent.ownerRef()).isEqualTo("10086");
        assertThat(sent.sourceKey()).isEqualTo("LINE-1");

        ActivityBag parent = bags.find(prepared.bag().id()).orElseThrow();
        assertThat(parent.closed()).isTrue();
        assertThat(parent.quantity()).isEqualByComparingTo("200");
        assertThat(parent.weight().value()).isEqualByComparingTo("1.500");
        assertThat(service.holderOf(parent.id())).isEqualTo(OutsourcingDispatchService.IN_HOUSE);
        ActivityBag outgoing = bags.find(sent.sentBagId()).orElseThrow();
        assertThat(outgoing.quantity()).isEqualByComparingTo("80");
        assertThat(outgoing.sourceKey()).isEqualTo("LINE-1");
        assertThat(outgoing.targetCode()).isEqualTo(parent.targetCode());
        assertThat(outgoing.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(outgoing.weight().value()).isNull();
        ActivityBag remain = bags.listByTarget(parent.targetCode()).stream()
                .filter(bag -> bag.parentId().equals(parent.id()) && !bag.id().equals(outgoing.id()))
                .findFirst().orElseThrow();
        assertThat(remain.quantity()).isEqualByComparingTo("120");
        assertThat(remain.sourceKey()).isEqualTo("LINE-1");
        assertThat(remain.weight().value()).isNull();
        assertThat(service.holderOf(remain.id())).isEqualTo(OutsourcingDispatchService.IN_HOUSE);
        assertThat(bags.listByTarget(parent.targetCode()).stream().filter(ActivityBag::activeLeaf)
                .map(ActivityBag::quantity).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("200");

        assertThatThrownBy(() -> service.dispatch("d02-over01", "clerk-01", prepared.task().taskId(),
                remain.id(), remain.version(), new BigDecimal("121")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能超发");
        assertThatThrownBy(() -> service.dispatch("d02-again1", "clerk-01", prepared.task().taskId(),
                outgoing.id(), outgoing.version(), outgoing.quantity()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该袋已发外");
        OutsourcingDispatch rest = service.dispatch("d02-rest01", "clerk-01", prepared.task().taskId(),
                remain.id(), remain.version(), remain.quantity());
        assertThat(rest.sentBagId()).isEqualTo(remain.id());
        assertThat(service.entrustedQuantity(prepared.task().taskId())).isEqualByComparingTo("200");
        assertThat(service.entrustedQuantity(prepared.task().taskId())).isEqualByComparingTo("200");
        assertThat(dispatches.listByTask(prepared.task().taskId())).hasSize(2);
    }

    @Test
    void 错袋错身份和未知重量不会写成出货() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryOutsourcingSourceRepository sources = new InMemoryOutsourcingSourceRepository();
        InMemoryOutsourcingDispatchRepository dispatches = new InMemoryOutsourcingDispatchRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        OutsourcingDispatchService service = service(sources, dispatches, bags, unit);
        Prepared prepared = prepare(sources, bags, unit, "BAG-WHOLE", "MD-20261030-0203",
                new BigDecimal("200"), StockMeasurement.unknown());
        OutsourcingDispatch whole = service.dispatch("d02-whole1", "clerk-01", prepared.task().taskId(),
                prepared.bag().id(), prepared.bag().version(), new BigDecimal("200"));
        ActivityBag bag = bags.find(prepared.bag().id()).orElseThrow();
        assertThat(bag.closed()).isFalse();
        assertThat(bag.weight().value()).isNull();
        assertThat(bag.quantity()).isEqualByComparingTo("200");
        assertThat(whole.sentBagId()).isEqualTo(bag.id());
        assertThat(service.entrustedQuantity(prepared.task().taskId())).isEqualByComparingTo("200");
        assertThat(service.holderOf(bag.id())).isEqualTo(prepared.task().partyId());
        assertThatThrownBy(() -> service.dispatch("d02-stale1", "clerk-01", prepared.task().taskId(),
                bag.id(), bag.version() + 3, BigDecimal.ONE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("袋已被其他人修改，请刷新后重试");

        ActivityBag other = saveBag(bags, unit, "BAG-OTHER", "MD-20261030-0203",
                new BigDecimal("5"), StockMeasurement.unknown(), "d02-bag-other");
        assertThatThrownBy(() -> service.dispatch("d02-other1", "clerk-01", prepared.task().taskId(),
                other.id(), other.version(), other.quantity()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("所发袋不属于该外协任务");
        assertThatThrownBy(() -> service.dispatch("d02-actor1", prepared.task().partyId(), prepared.task().taskId(),
                other.id(), other.version(), BigDecimal.ONE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("经办人不是外协方");

        Prepared gold = prepare(sources, bags, unit, "BAG-GOLD", "GJ-20261030-0001",
                new BigDecimal("3"), StockMeasurement.unknown());
        assertThatThrownBy(() -> service.dispatch("d02-gold01", "clerk-01", gold.task().taskId(),
                gold.bag().id(), gold.bag().version(), gold.bag().quantity()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本版金工重量只作记录，不能按三位库存入账");
        assertThat(dispatches.listByTask(gold.task().taskId())).isEmpty();
        assertThat(service.holderOf(gold.bag().id())).isEqualTo(OutsourcingDispatchService.IN_HOUSE);
    }

    @Test
    void 失败不留下分袋和发外() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryOutsourcingSourceRepository sources = new InMemoryOutsourcingSourceRepository();
        InMemoryOutsourcingDispatchRepository dispatches = new InMemoryOutsourcingDispatchRepository();
        MemoryBusinessWorkUnit ok = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        Prepared prepared = prepare(sources, bags, ok, "BAG-FAIL", "MD-20261030-0204",
                new BigDecimal("30"), StockMeasurement.unknown());
        OutsourcingDispatchService failing = service(sources, dispatches, bags, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.dispatch("d02-fail01", "clerk-01", prepared.task().taskId(),
                prepared.bag().id(), prepared.bag().version(), new BigDecimal("10")))
                .isInstanceOf(IllegalStateException.class);
        ActivityBag bag = bags.find(prepared.bag().id()).orElseThrow();
        assertThat(bag.closed()).isFalse();
        assertThat(bag.quantity()).isEqualByComparingTo("30");
        assertThat(bags.listByTarget(bag.targetCode())).hasSize(1);
        assertThat(dispatches.listByTask(prepared.task().taskId())).isEmpty();
    }

    @Test
    void jdbc并发不超发且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcActivityBagRepository bags = new JdbcActivityBagRepository(dataSource);
        JdbcOutsourcingSourceRepository sources = new JdbcOutsourcingSourceRepository(dataSource);
        JdbcOutsourcingDispatchRepository dispatches = new JdbcOutsourcingDispatchRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        OutsourcingDispatchService service = service(sources, dispatches, bags, unit);
        Prepared prepared = prepare(sources, bags, unit, "BAG-RACE", "MD-20261030-0205",
                new BigDecimal("50"), StockMeasurement.unknown());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = pool.submit(() -> attempt(service, prepared, "d02-race01", start));
            Future<Object> second = pool.submit(() -> attempt(service, prepared, "d02-race02", start));
            start.countDown();
            Object left = first.get(10, TimeUnit.SECONDS);
            Object right = second.get(10, TimeUnit.SECONDS);
            long wins = List.of(left, right).stream().filter(OutsourcingDispatch.class::isInstance).count();
            assertThat(wins).isEqualTo(1);
            assertThat(List.of(left, right)).anySatisfy(item -> assertThat(item).isInstanceOf(BusinessException.class));
        } finally {
            pool.shutdownNow();
        }
        assertThat(service.entrustedQuantity(prepared.task().taskId())).isEqualByComparingTo("50");
        assertThat(dispatches.listByTask(prepared.task().taskId())).hasSize(1);
        ActivityBag raced = bags.find(prepared.bag().id()).orElseThrow();
        assertThat(raced.quantity()).isEqualByComparingTo("50");
        assertThat(raced.weight().value()).isNull();
        assertThat(raced.closed()).isFalse();

        Prepared rollback = prepare(sources, bags, unit, "BAG-ROLL", "MD-20261030-0206",
                new BigDecimal("30"), StockMeasurement.unknown());
        OutsourcingDispatchService failing = service(sources, dispatches, bags, new JdbcBusinessWorkUnit(
                dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.dispatch("d02-jfail1", "clerk-01", rollback.task().taskId(),
                rollback.bag().id(), rollback.bag().version(), new BigDecimal("10")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(bags.find(rollback.bag().id()).orElseThrow().closed()).isFalse();
        assertThat(bags.listByTarget(rollback.bag().targetCode())).hasSize(1);
        assertThat(dispatches.listByTask(rollback.task().taskId())).isEmpty();
    }

    private static Object attempt(OutsourcingDispatchService service, Prepared prepared, String requestId,
                                  CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("并发启动超时");
            }
            return service.dispatch(requestId, "clerk-01", prepared.task().taskId(),
                    prepared.bag().id(), prepared.bag().version(), prepared.bag().quantity());
        } catch (RuntimeException ex) {
            return ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ex;
        }
    }

    private static OutsourcingDispatchService service(com.zhubao.erp.business.domain.OutsourcingSourceRepository sources,
                                                      OutsourcingDispatchRepository dispatches,
                                                      ActivityBagRepository bags, BusinessWorkUnit unit) {
        return new OutsourcingDispatchService(sources, dispatches, new InMemoryOutsourcingReturnRepository(), bags,
                new BagSplitService(bags, new ActivityBagService(bags), unit), unit);
    }

    private static Prepared prepare(com.zhubao.erp.business.domain.OutsourcingSourceRepository sources,
                                    ActivityBagRepository bags, BusinessWorkUnit unit, String bagId, String target,
                                    BigDecimal quantity, StockMeasurement.Weight weight) {
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        BusinessKind kind = target.startsWith("GJ-") ? BusinessKind.GOLDWORK : BusinessKind.PROCESSING;
        String material = kind == BusinessKind.GOLDWORK ? "足金" : "牙骨";
        String node = kind == BusinessKind.GOLDWORK ? "POLISH_GOLD" : "POLISH_BEAD";
        routes.insert(new RouteSnapshot("RS-" + target, kind, target, 1,
                ProcessCatalog.defaults(kind, material).stream().map(ProcessStep::id).toList(), true), null);
        ActivityBag bag = saveBag(bags, unit, bagId, target, quantity, weight, "bag-" + bagId);
        OutsourcingSourceService sourcesService = new OutsourcingSourceService(sources, bags, routes, unit);
        if (sources.findParty("WX-POLISH-01").isEmpty()) {
            sourcesService.register("reg-" + bagId, OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "甲抛光", "clerk-01");
        }
        OutsourcingTask task = sourcesService.openTask("task-" + bagId, "WX-POLISH-01", target, node,
                bag.id(), OwnerType.CUSTOMER, "10086", "clerk-01");
        return new Prepared(bag, task);
    }

    private static ActivityBag saveBag(ActivityBagRepository bags, BusinessWorkUnit unit, String bagId, String target,
                                       BigDecimal quantity, StockMeasurement.Weight weight, String requestId) {
        ActivityBag bag = new ActivityBag(bagId, target, "牙骨", "REQ-1", "LINE-1", "", false, quantity, weight, 1);
        unit.execute(new CommandCall(requestId, "clerk-01", "SAVE_BAG", "BAG", bagId, bagId), session -> {
            bags.save(bag, 0, session);
            return bag.id();
        });
        return bags.find(bagId).orElseThrow();
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V81__m3_activity_bag.sql",
                    "V88__m3_outsourcing_source.sql", "V89__m3_outsourcing_dispatch.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }

    private record Prepared(ActivityBag bag, OutsourcingTask task) {
    }
}
