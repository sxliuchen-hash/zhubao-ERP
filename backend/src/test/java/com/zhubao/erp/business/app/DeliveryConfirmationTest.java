package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.DeliveryConfirmation;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliverySourceSelection;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionRelease;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryDeliveryDraftRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryPortionInspectionRepository;
import com.zhubao.erp.business.infra.InMemoryPortionReleaseRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.business.infra.JdbcBusinessDocumentRepository;
import com.zhubao.erp.business.infra.JdbcDeliveryDraftRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryConfirmationTest {

    @Test
    void 一次确认两袋且汇总不再扣库存() {
        Fixture fixture = memoryFixture();
        receive(fixture.stock, finished("MD-20261030-1001", "MD-20261030-1001"), "QC-MD", "110", "7");
        fixture.stock.transfer(new StockTransferCommand(
                "outbound_shipment", "OUT-HIST-01",
                finished("MD-20261030-1001", "MD-20261030-1001"),
                shipped("MD-20261030-1001", "MD-20261030-1001"),
                new BigDecimal("30"), new BigDecimal("2"), "历史出库"));
        fixture.documents.saveOutboundOrder(history());
        receive(fixture.stock, finished("MD-20261030-1001", "ZD-20261030-1002"), "QC-ZD", "40", "3");
        saveBag(fixture, "BAG-B", "MD-20261030-1001", "80", "5.000", "e02-bag-b01");
        saveBag(fixture, "BAG-A", "ZD-20261030-1002", "40", "3", "e02-bag-a01");
        var sheet = fixture.drafts.open("e02-draft01", "clerk-01", 7L, "顺丰", "两袋", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗"),
                new DeliverySourceSelection("BAG-A", 1, "ZD-20261030-1002", "件")));
        int ledgersBefore = fixture.stock.ledgers().size();

        DeliveryConfirmation confirmed = fixture.confirmation.confirm(
                "e02-confirm", "clerk-01", sheet.draft().deliveryId(), sheet.draft().version());
        DeliveryConfirmation replay = fixture.confirmation.confirm(
                "e02-confirm", "clerk-01", sheet.draft().deliveryId(), sheet.draft().version());

        assertThat(replay.draft().deliveryId()).isEqualTo(confirmed.draft().deliveryId());
        assertThat(replay.sources()).extracting(source -> source.ledgerCode())
                .containsExactlyElementsOf(confirmed.sources().stream().map(source -> source.ledgerCode()).toList());
        assertThat(confirmed.draft().status()).isEqualTo(DeliveryDraftStatus.CONFIRMED);
        assertThat(confirmed.draft().version()).isEqualTo(2);
        assertThat(confirmed.sources()).hasSize(2);
        assertThat(confirmed.sources()).allSatisfy(source ->
                assertThat(source.outboundCode()).startsWith(confirmed.draft().deliveryId() + "-"));
        assertThat(confirmed.sources()).extracting(source -> source.quantity())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("80"), new BigDecimal("40"));
        List<StockLedgerEntry> shipments = fixture.stock.ledgers().stream()
                .filter(entry -> "outbound_shipment".equals(entry.documentType())).toList();
        assertThat(fixture.stock.ledgers()).hasSize(ledgersBefore + 2);
        assertThat(shipments).hasSize(3);
        assertThat(shipments).noneMatch(entry -> confirmed.draft().deliveryId().equals(entry.documentCode()));
        assertThat(shipments).noneMatch(entry -> entry.quantity().compareTo(new BigDecimal("120")) == 0
                || entry.quantity().compareTo(new BigDecimal("150")) == 0);
        assertThat(fixture.ledger.findBalance(finished("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("0");
        assertThat(fixture.ledger.findBalance(shipped("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("110");
        assertThat(fixture.ledger.findBalance(finished("MD-20261030-1001", "ZD-20261030-1002")).quantity())
                .isEqualByComparingTo("0");
        assertThat(fixture.ledger.findBalance(shipped("MD-20261030-1001", "ZD-20261030-1002")).quantity())
                .isEqualByComparingTo("40");
        OutboundOrderRecord historical = fixture.documents.findOutboundOrderByCode("OUT-HIST-01").orElseThrow();
        assertThat(historical.quantity()).isEqualByComparingTo("30");
        assertThat(fixture.documents.listOutboundOrders()).hasSize(3);
        assertThat(fixture.bags.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("80");
        assertThat(fixture.bags.find("BAG-B").orElseThrow().version()).isEqualTo(1);
        assertThat(fixture.bags.find("BAG-A").orElseThrow().weight().value()).isEqualByComparingTo("3");

        var rival = fixture.drafts.open("e02-draft02", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗")));
        assertThatThrownBy(() -> fixture.confirmation.confirm(
                "e02-rival01", "clerk-01", rival.draft().deliveryId(), rival.draft().version()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该袋已经交付");
        assertThat(fixture.stock.ledgers()).hasSize(ledgersBefore + 2);
        assertThat(fixture.documents.listOutboundOrders()).hasSize(3);
        assertThat(fixture.ledger.findBalance(shipped("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("110");
    }

    @Test
    void 失败明细和未核定来源整单回滚() {
        Fixture fixture = memoryFixture();
        receive(fixture.stock, finished("MD-20261030-1001", "MD-20261030-1001"), "QC-MD2", "80", "5");
        receive(fixture.stock, finished("MD-20261030-1001", "ZD-20261030-1002"), "QC-ZD2", "10", "3");
        saveBag(fixture, "BAG-B", "MD-20261030-1001", "80", "5", "e02-bag-b02");
        saveBag(fixture, "BAG-A", "ZD-20261030-1002", "40", "3", "e02-bag-a02");
        var shortStock = fixture.drafts.open("e02-short01", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗"),
                new DeliverySourceSelection("BAG-A", 1, "ZD-20261030-1002", "件")));
        assertThatThrownBy(() -> fixture.confirmation.confirm(
                "e02-shortcf", "clerk-01", shortStock.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("来源库存数量不足");
        assertThat(fixture.store.find(shortStock.draft().deliveryId()).orElseThrow().status())
                .isEqualTo(DeliveryDraftStatus.DRAFT);
        assertThat(fixture.store.confirmedSources(shortStock.draft().deliveryId())).isEmpty();
        assertThat(fixture.documents.listOutboundOrders()).isEmpty();
        assertThat(fixture.ledger.findBalance(finished("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("80");
        assertThat(fixture.ledger.findBalance(shipped("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("0");

        saveBag(fixture, "BAG-U", "MD-20261030-1001", "80", null, "e02-bag-unk");
        var unknown = fixture.drafts.open("e02-unkn01", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-U", 1, "MD-20261030-1001", "颗")));
        assertThatThrownBy(() -> fixture.confirmation.confirm("e02-unkncf", "clerk-01", unknown.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("需要本份额的实际重量后才能转移或发货");
        assertThat(fixture.ledger.findBalance(finished("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("80");

        saveBag(fixture, "BAG-DY", "DY-20261030-0001", "2", "1", "e02-bag-dy1");
        var sampling = fixture.drafts.open("e02-sample", "clerk-01", 9L, "", "", List.of(
                new DeliverySourceSelection("BAG-DY", 1, "DY-20261030-0001", "颗")));
        assertThat(sampling.draft().businessKind()).isEqualTo(BusinessKind.SAMPLING);
        assertThatThrownBy(() -> fixture.confirmation.confirm("e02-sampcf", "clerk-01", sampling.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样和金工的交付确认尚未具备各自的库存来源，不能改记成普通工单");
        assertThat(fixture.documents.findMotherOrderByCode("DY-20261030-0001")).isEmpty();
        assertThat(fixture.documents.listOutboundOrders()).isEmpty();
        assertThat(fixture.bags.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("80");
    }

    @Test
    void jdbc竞争确认只出库一次且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcStockLedgerRepository ledger = new JdbcStockLedgerRepository(dataSource);
        StockLedgerService stock = new StockLedgerService(ledger);
        JdbcActivityBagRepository bags = new JdbcActivityBagRepository(dataSource);
        JdbcDeliveryDraftRepository draftStore = new JdbcDeliveryDraftRepository(dataSource);
        JdbcBusinessDocumentRepository documents = new JdbcBusinessDocumentRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        InMemoryBusinessDocumentRepository customers = customers();
        DeliveryDraftService draftService = new DeliveryDraftService(
                draftStore, bags, new DocumentProcessingTargetCustomer(customers), bagId -> true, unit);
        InMemoryPortionReleaseRepository releases = new InMemoryPortionReleaseRepository();
        DeliveryConfirmationService confirmation = new DeliveryConfirmationService(
                draftStore, bags, new DocumentDeliveryStockPlaceLookup(customers), bagId -> true,
                stock, documents, unit, releases, new InMemoryRouteSnapshotRepository(), new InMemoryNodePortionReader());
        releases.save(new PortionRelease("BAG-B", new BigDecimal("80"), BigDecimal.ZERO), null);
        releases.save(new PortionRelease("BAG-A", new BigDecimal("40"), BigDecimal.ZERO), null);
        receive(stock, finished("MD-20261030-1001", "MD-20261030-1001"), "QC-J1", "80", "5");
        saveBag(bags, unit, "BAG-B", "MD-20261030-1001", "80", "5", "e02-jbag-b1");
        var first = draftService.open("e02-jdraft1", "clerk-01", 7L, "顺丰", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗")));
        var second = draftService.open("e02-jdraft2", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> left = pool.submit(() -> attempt(confirmation, first.draft().deliveryId(), "e02-jconf01", start));
            Future<Object> right = pool.submit(() -> attempt(confirmation, second.draft().deliveryId(), "e02-jconf02", start));
            start.countDown();
            Object one = left.get(10, TimeUnit.SECONDS);
            Object other = right.get(10, TimeUnit.SECONDS);
            long wins = List.of(one, other).stream().filter(DeliveryConfirmation.class::isInstance).count();
            assertThat(wins).isEqualTo(1);
            assertThat(List.of(one, other)).anySatisfy(item -> assertThat(item).isInstanceOf(BusinessException.class));
        } finally {
            pool.shutdownNow();
        }
        assertThat(ledger.findBalance(finished("MD-20261030-1001", "MD-20261030-1001")).quantity()).isEqualByComparingTo("0");
        assertThat(ledger.findBalance(shipped("MD-20261030-1001", "MD-20261030-1001")).quantity()).isEqualByComparingTo("80");
        assertThat(stock.ledgers().stream().filter(entry -> "outbound_shipment".equals(entry.documentType()))).hasSize(1);
        assertThat(documents.listOutboundOrders()).hasSize(1);
        assertThat(bags.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("80");

        receive(stock, finished("MD-20261030-1001", "ZD-20261030-1002"), "QC-J2", "10", "3");
        saveBag(bags, unit, "BAG-A", "ZD-20261030-1002", "40", "3", "e02-jbag-a1");
        var failing = draftService.open("e02-jfail-d", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-A", 1, "ZD-20261030-1002", "件")));
        assertThatThrownBy(() -> confirmation.confirm("e02-jfailcf", "clerk-01", failing.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("来源库存数量不足");
        assertThat(draftStore.find(failing.draft().deliveryId()).orElseThrow().status()).isEqualTo(DeliveryDraftStatus.DRAFT);
        assertThat(draftStore.confirmedSources(failing.draft().deliveryId())).isEmpty();
        assertThat(ledger.findBalance(finished("MD-20261030-1001", "ZD-20261030-1002")).quantity()).isEqualByComparingTo("10");
        assertThat(documents.listOutboundOrders()).hasSize(1);
    }

    private static Object attempt(DeliveryConfirmationService confirmation, String deliveryId, String requestId,
                                  CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("并发启动超时");
            }
            return confirmation.confirm(requestId, "clerk-01", deliveryId, 1);
        } catch (RuntimeException ex) {
            return ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ex;
        }
    }

    private static Fixture memoryFixture() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryBusinessDocumentRepository documents = customers();
        InMemoryDeliveryDraftRepository draftStore = new InMemoryDeliveryDraftRepository();
        InMemoryStockLedgerRepository ledger = new InMemoryStockLedgerRepository();
        StockLedgerService stock = new StockLedgerService(ledger);
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryPortionReleaseRepository releases = new InMemoryPortionReleaseRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        DeliveryDraftService drafts = new DeliveryDraftService(
                draftStore, bags, new DocumentProcessingTargetCustomer(documents), bagId -> true, unit);
        DeliveryConfirmationService confirmation = new DeliveryConfirmationService(
                draftStore, bags, new DocumentDeliveryStockPlaceLookup(documents), bagId -> true,
                stock, documents, unit, releases, routes, portions);
        return new Fixture(bags, documents, draftStore, drafts, confirmation, stock, ledger, unit, releases, routes, portions);
    }

    private static InMemoryBusinessDocumentRepository customers() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(mother("MD-20261030-1001", 7L, "颗"));
        documents.saveChildWorkOrder(child("ZD-20261030-1002", "MD-20261030-1001", 7L, "件"));
        return documents;
    }

    private static void saveBag(Fixture fixture, String bagId, String target, String quantity, String weight, String requestId) {
        saveBag(fixture.bags, fixture.unit, bagId, target, quantity, weight, requestId);
        fixture.releases.save(new PortionRelease(bagId, new BigDecimal(quantity), BigDecimal.ZERO), null);
    }

    private static void saveBag(ActivityBagRepository bags, com.zhubao.erp.work.app.BusinessWorkUnit unit,
                                String bagId, String target, String quantity, String weight, String requestId) {
        StockMeasurement.Weight measured = weight == null
                ? StockMeasurement.unknown()
                : StockMeasurement.known(new BigDecimal(weight));
        ActivityBag bag = new ActivityBag(bagId, target, "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal(quantity), measured, 1);
        unit.execute(new CommandCall(requestId, "clerk-01", "SAVE_BAG", "BAG", bagId, bagId), session -> {
            bags.save(bag, 0, session);
            return bag.id();
        });
    }

    private static void receive(StockLedgerService stock, StockDimension to, String code, String quantity, String weight) {
        stock.transfer(new StockTransferCommand("quality_release", code, null, to,
                new BigDecimal(quantity), new BigDecimal(weight), "预置成品"));
    }

    private static StockDimension finished(String mother, String work) {
        return dimension(mother, work, StockStatus.FINISHED);
    }

    private static StockDimension shipped(String mother, String work) {
        return dimension(mother, work, StockStatus.SHIPPED);
    }

    private static StockDimension dimension(String mother, String work, StockStatus status) {
        return new StockDimension(OwnerType.CUSTOMER, 7L, 7L, mother, work, "牙骨", "WH-01", status);
    }

    private static OutboundOrderRecord history() {
        return new OutboundOrderRecord("OUT-HIST-01", "MD-20261030-1001", "MD-20261030-1001", 7L, 7L, "牙骨",
                "WH-01", "WH-01", "2026-10-29", "历史", new BigDecimal("30"), "颗", new BigDecimal("2"),
                "", "旧物流", "", "", "", null, "", "clerk-01", "", "", "SHIPPED", "STK-HIST",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.parse("2026-10-29T10:00:00")));
    }

    private static MotherOrderRecord mother(String code, long customerId, String unit) {
        return new MotherOrderRecord(code, customerId, customerId, "牙骨",
                null, null, null, null, null, null, null, "CUSTOMER", true, "WH-01",
                new BigDecimal("120"), unit, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static ChildWorkOrderRecord child(String code, String motherCode, long customerId, String unit) {
        return new ChildWorkOrderRecord(code, motherCode, customerId, "客户", "要求", null,
                "牙骨", "货品", new BigDecimal("40"), unit, null, "WH-01", "", null, null, "clerk", null);
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = StockRepositoryFixture.jdbcDataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V81__m3_activity_bag.sql",
                    "V92__m3_delivery_draft.sql", "V143__m3_delivery_presentation_snapshot.sql", "V93__m3_delivery_confirmation.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
            statement.execute("""
                    create table biz_outbound_order (
                        id bigint generated by default as identity primary key,
                        code varchar(80) not null,
                        mother_order_code varchar(80) not null,
                        work_order_code varchar(90) not null,
                        customer_id bigint not null,
                        owner_customer_id bigint,
                        material_code varchar(80) not null,
                        from_warehouse_code varchar(50) not null,
                        to_warehouse_code varchar(50) not null,
                        delivery_date varchar(20),
                        delivery_category varchar(80),
                        delivery_quantity decimal(18,3) not null,
                        delivery_quantity_unit varchar(20) not null,
                        delivery_weight decimal(18,3) not null,
                        express_company varchar(80),
                        express_no varchar(80) not null,
                        logistics_raw_text varchar(160),
                        handler_name_snapshot varchar(120),
                        source_register_type varchar(64),
                        source_line_no int,
                        source_sequence_no varchar(40),
                        operator varchar(80) not null,
                        evidence_code varchar(120),
                        outbound_remark varchar(500),
                        order_status varchar(40) not null,
                        ledger_code varchar(80) not null,
                        created_at varchar(40) not null,
                        unique (code),
                        unique (ledger_code)
                    )
                    """);
        }
        return dataSource;
    }

    @Test
    void 没有放行冻结或穿串未完都不能确认发货() {
        Fixture fixture = memoryFixture();
        receive(fixture.stock, finished("MD-20261030-1001", "MD-20261030-1001"), "QC-RF08", "10", "1");
        saveBag(fixture.bags, fixture.unit, "BAG-HOLD", "MD-20261030-1001", "10", "1", "rf08-bag-01");
        var draft = fixture.drafts.open("rf08-draft1", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-HOLD", 1, "MD-20261030-1001", "颗")));
        assertThatThrownBy(() -> fixture.confirmation.confirm("rf08-conf01", "clerk-01", draft.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("质量尚未放行");
        assertThat(fixture.documents.listOutboundOrders()).isEmpty();

        fixture.releases.save(new PortionRelease("BAG-HOLD", new BigDecimal("10"), new BigDecimal("10")), null);
        assertThatThrownBy(() -> fixture.confirmation.confirm("rf08-conf02", "clerk-01", draft.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("冻结份额不能发出");

        fixture.releases.save(new PortionRelease("BAG-HOLD", new BigDecimal("10"), BigDecimal.ZERO), null);
        fixture.routes.insert(new RouteSnapshot(
                "RS-RF08", BusinessKind.PROCESSING, "MD-20261030-1001", 1, List.of("STRING"), true), null);
        assertThatThrownBy(() -> fixture.confirmation.confirm("rf08-conf03", "clerk-01", draft.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("穿串未完，不能仅凭质检合格发出");
        assertThat(fixture.ledger.findBalance(finished("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("10");
    }

    @Test
    void 路线上未做完的工序不能凭其他成品余额发出() {
        Fixture fixture = memoryFixture();
        AtomicReference<BigDecimal> pending = new AtomicReference<>(BigDecimal.ZERO);
        InMemoryPortionInspectionRepository inspections = new InMemoryPortionInspectionRepository();
        DeliveryConfirmationService confirmation = new DeliveryConfirmationService(
                fixture.store(), fixture.bags(), new DocumentDeliveryStockPlaceLookup(fixture.documents()),
                bagId -> true, fixture.stock(), fixture.documents(), fixture.unit(),
                fixture.releases(), fixture.routes(), fixture.portions(),
                (bagId, stepId) -> pending.get(), inspections);
        receive(fixture.stock(), finished("MD-20261030-1001", "MD-20261030-1001"), "QC-RF08B", "10", "1");
        saveBag(fixture, "BAG-STEP", "MD-20261030-1001", "10", "1", "rf08-bag-02");
        fixture.routes().insert(new RouteSnapshot(
                "RS-RF08B", BusinessKind.PROCESSING, "MD-20261030-1001", 1, List.of("CUT", "BLANK"), true), null);
        var blocked = fixture.drafts().open("rf08-draft2", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-STEP", 1, "MD-20261030-1001", "颗")));
        assertThatThrownBy(() -> confirmation.confirm("rf08-step01", "clerk-01", blocked.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工序未完，不能发出");
        fixture.portions().save(new NodePortionBalance("BAG-STEP", "CUT", new BigDecimal("10"), new BigDecimal("10")), null);
        fixture.portions().save(new NodePortionBalance("BAG-STEP", "BLANK", new BigDecimal("10"), BigDecimal.ZERO), null);
        assertThatThrownBy(() -> confirmation.confirm("rf08-step02", "clerk-01", blocked.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工序未完，不能发出");
        fixture.portions().save(new NodePortionBalance("BAG-STEP", "BLANK", new BigDecimal("10"), new BigDecimal("10")), null);
        pending.set(BigDecimal.ONE);
        assertThatThrownBy(() -> confirmation.confirm("rf08-step03", "clerk-01", blocked.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("待核数量未清，不能发出");
        pending.set(BigDecimal.ZERO);
        inspections.save(new PortionInspection(
                "QC" + "c".repeat(32), "rf08-qc01", "BAG-STEP", "REQ-1",
                new BigDecimal("8"), new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO, List.of()), null);
        assertThatThrownBy(() -> confirmation.confirm("rf08-step04", "clerk-01", blocked.draft().deliveryId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("返工未完，不能发出");
        assertThat(fixture.documents().listOutboundOrders()).isEmpty();
        assertThat(fixture.ledger().findBalance(finished("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("10");

        saveBag(fixture, "BAG-DONE", "MD-20261030-1001", "10", "1", "rf08-bag-03");
        fixture.portions().save(new NodePortionBalance("BAG-DONE", "CUT", new BigDecimal("10"), new BigDecimal("10")), null);
        fixture.portions().save(new NodePortionBalance("BAG-DONE", "BLANK", new BigDecimal("10"), new BigDecimal("10")), null);
        var ready = fixture.drafts().open("rf08-draft3", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-DONE", 1, "MD-20261030-1001", "颗")));
        confirmation.confirm("rf08-step05", "clerk-01", ready.draft().deliveryId(), 1);
        assertThat(fixture.documents().listOutboundOrders()).hasSize(1);
        assertThat(fixture.ledger().findBalance(finished("MD-20261030-1001", "MD-20261030-1001")).quantity())
                .isEqualByComparingTo("0");
    }

    private record Fixture(
            InMemoryActivityBagRepository bags,
            InMemoryBusinessDocumentRepository documents,
            InMemoryDeliveryDraftRepository store,
            DeliveryDraftService drafts,
            DeliveryConfirmationService confirmation,
            StockLedgerService stock,
            InMemoryStockLedgerRepository ledger,
            MemoryBusinessWorkUnit unit,
            InMemoryPortionReleaseRepository releases,
            InMemoryRouteSnapshotRepository routes,
            InMemoryNodePortionReader portions
    ) {
    }
}
