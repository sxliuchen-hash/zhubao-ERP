package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.CustomerReturnSource;
import com.zhubao.erp.business.domain.CustomerReturnStatus;
import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliverySourceSelection;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryCustomerReturnRepository;
import com.zhubao.erp.business.infra.InMemoryDeliveryDraftRepository;
import com.zhubao.erp.business.infra.JdbcCustomerReturnRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerReturnSourceTest {

    @Test
    void 退回原交付份额待复核且不超过原数() {
        Fixture fixture = memory();
        Prepared prepared = prepare(fixture, "e04-draft1", "e04-bag-b01");
        CustomerReturnSource first = fixture.returns.receive(
                "e04-ret-01", "clerk-01", prepared.deliveryId(), prepared.outboundCode(),
                new BigDecimal("30"), new BigDecimal("2"));
        CustomerReturnSource replay = fixture.returns.receive(
                "e04-ret-01", "clerk-01", prepared.deliveryId(), prepared.outboundCode(),
                new BigDecimal("30"), new BigDecimal("2"));
        assertThat(replay.returnId()).isEqualTo(first.returnId());
        assertThat(first.status()).isEqualTo(CustomerReturnStatus.PENDING_REVIEW);
        assertThat(first.adjustmentBasis()).isEqualTo(CustomerReturnSource.UNDECIDED_BASIS);
        assertThat(first.adjustmentBasis()).doesNotContain("0");
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.SHIPPED)).quantity()).isEqualByComparingTo("50");
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.FROZEN)).quantity()).isEqualByComparingTo("30");
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.FINISHED)).quantity()).isEqualByComparingTo("0");
        assertThat(fixture.documents.findOutboundOrderByCode(prepared.outboundCode()).orElseThrow().quantity())
                .isEqualByComparingTo("80");
        assertThat(fixture.store.confirmedSources(prepared.deliveryId()).get(0).quantity()).isEqualByComparingTo("80");
        assertThat(fixture.bags.find("BAG-B").orElseThrow().targetCode()).isEqualTo("MD-20261030-1001");
        assertThat(fixture.documents.listMotherOrders()).hasSize(1);
        assertThat(fixture.documents.listAllChildWorkOrders()).isEmpty();

        assertThatThrownBy(() -> fixture.returns.receive(
                "e04-ret-02", "clerk-01", prepared.deliveryId(), prepared.outboundCode(),
                new BigDecimal("51"), new BigDecimal("1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退货数量不得超过有效原交付");
        assertThatThrownBy(() -> fixture.returns.receive(
                "e04-ret-03", "clerk-01", prepared.deliveryId(), prepared.outboundCode(),
                new BigDecimal("20"), new BigDecimal("4")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退货重量不得超过有效原交付");
        assertThatThrownBy(() -> fixture.returns.receive(
                "e04-ret-04", "clerk-01", prepared.deliveryId(), prepared.outboundCode(),
                new BigDecimal("20"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("需要本份额的实际重量后才能转移或发货");
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.SHIPPED)).quantity()).isEqualByComparingTo("50");
        assertThat(fixture.returnStore.listByOutbound(prepared.outboundCode())).hasSize(1);

        CustomerReturnSource rest = fixture.returns.receive(
                "e04-ret-05", "clerk-01", prepared.deliveryId(), prepared.outboundCode(),
                new BigDecimal("50"), new BigDecimal("3"));
        assertThat(rest.status()).isEqualTo(CustomerReturnStatus.PENDING_REVIEW);
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.SHIPPED)).quantity()).isEqualByComparingTo("0");
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.FROZEN)).quantity()).isEqualByComparingTo("80");
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.FINISHED)).quantity()).isEqualByComparingTo("0");
        assertThat(fixture.documents.findOutboundOrderByCode(prepared.outboundCode()).orElseThrow().quantity())
                .isEqualByComparingTo("80");
    }

    @Test
    void 失败不留下退货和库存() {
        Fixture fixture = memory();
        Prepared prepared = prepare(fixture, "e04-draft2", "e04-bag-b02");
        CustomerReturnSourceService failing = new CustomerReturnSourceService(
                fixture.store, new DocumentDeliveryStockPlaceLookup(fixture.documents), fixture.returnStore,
                fixture.documents, fixture.stock, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.receive("e04-fail01", "clerk-01", prepared.deliveryId(),
                prepared.outboundCode(), new BigDecimal("10"), new BigDecimal("1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.returnStore.listByOutbound(prepared.outboundCode())).isEmpty();
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.SHIPPED)).quantity()).isEqualByComparingTo("80");
        assertThat(fixture.ledger.findBalance(dimension(StockStatus.FROZEN)).quantity()).isEqualByComparingTo("0");
        assertThat(fixture.documents.findOutboundOrderByCode(prepared.outboundCode()).orElseThrow().quantity())
                .isEqualByComparingTo("80");
    }

    @Test
    void jdbc退回可回放且超额不入账() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcStockLedgerRepository ledger = new JdbcStockLedgerRepository(dataSource);
        StockLedgerService stock = new StockLedgerService(ledger);
        InMemoryBusinessDocumentRepository documents = documents();
        InMemoryDeliveryDraftRepository store = new InMemoryDeliveryDraftRepository();
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        MemoryBusinessWorkUnit setup = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        Prepared prepared = prepare(documents, store, bags, stock, setup, "e04-jdraft", "e04-jbag01");
        JdbcCustomerReturnRepository returnStore = new JdbcCustomerReturnRepository(dataSource);
        CustomerReturnSourceService service = new CustomerReturnSourceService(
                store, new DocumentDeliveryStockPlaceLookup(documents), returnStore, documents, stock,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)));
        CustomerReturnSource first = service.receive("e04-jret01", "clerk-01", prepared.deliveryId(),
                prepared.outboundCode(), new BigDecimal("30"), new BigDecimal("2"));
        CustomerReturnSource replay = service.receive("e04-jret01", "clerk-01", prepared.deliveryId(),
                prepared.outboundCode(), new BigDecimal("30"), new BigDecimal("2"));
        assertThat(replay.returnId()).isEqualTo(first.returnId());
        assertThat(first.status()).isEqualTo(CustomerReturnStatus.PENDING_REVIEW);
        assertThat(ledger.findBalance(dimension(StockStatus.SHIPPED)).quantity()).isEqualByComparingTo("50");
        assertThat(ledger.findBalance(dimension(StockStatus.FROZEN)).quantity()).isEqualByComparingTo("30");
        assertThat(ledger.findBalance(dimension(StockStatus.FINISHED)).quantity()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.receive("e04-jret02", "clerk-01", prepared.deliveryId(),
                prepared.outboundCode(), new BigDecimal("51"), new BigDecimal("1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退货数量不得超过有效原交付");
        assertThat(returnStore.listByOutbound(prepared.outboundCode())).hasSize(1);
        assertThat(ledger.findBalance(dimension(StockStatus.SHIPPED)).quantity()).isEqualByComparingTo("50");
        assertThat(documents.findOutboundOrderByCode(prepared.outboundCode()).orElseThrow().quantity())
                .isEqualByComparingTo("80");
    }

    private static Prepared prepare(Fixture fixture, String requestId, String bagRequest) {
        return prepare(fixture.documents, fixture.store, fixture.bags, fixture.stock, fixture.unit, requestId, bagRequest);
    }

    private static Prepared prepare(InMemoryBusinessDocumentRepository documents, InMemoryDeliveryDraftRepository store,
                                    InMemoryActivityBagRepository bags, StockLedgerService stock,
                                    com.zhubao.erp.work.app.BusinessWorkUnit unit, String requestId, String bagRequest) {
        if (documents.findMotherOrderByCode("MD-20261030-1001").isEmpty()) {
            documents.saveMotherOrder(mother());
        }
        bags.save(new ActivityBag("BAG-B", "MD-20261030-1001", "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("80"), StockMeasurement.known(new BigDecimal("5")), 1), 0, null);
        DeliveryDraftService drafts = new DeliveryDraftService(
                store, bags, new DocumentProcessingTargetCustomer(documents), bagId -> true, unit);
        var sheet = drafts.open(requestId, "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗")));
        String outboundCode = sheet.draft().deliveryId() + "-1";
        unit.execute(new CommandCall("e04-seed-" + requestId, "clerk-01", "SEED_CONFIRM", "DELIVERY_DRAFT",
                sheet.draft().deliveryId(), sheet.draft().deliveryId()), session -> {
            store.confirm(new DeliveryDraft(
                    sheet.draft().deliveryId(), sheet.draft().requestId(), 7L, sheet.draft().businessKind(),
                    "", "", DeliveryDraftStatus.CONFIRMED, 2), List.of(new DeliveryConfirmedSource(
                    sheet.draft().deliveryId(), 1, "BAG-B", outboundCode, "STK-E04",
                    new BigDecimal("80"), new BigDecimal("5"))), 1, session);
            return sheet.draft().deliveryId();
        });
        documents.saveOutboundOrder(outbound(outboundCode));
        stock.transfer(new StockTransferCommand("outbound_shipment", outboundCode, null,
                dimension(StockStatus.SHIPPED), new BigDecimal("80"), new BigDecimal("5"), "原交付"));
        return new Prepared(sheet.draft().deliveryId(), outboundCode);
    }

    private static Fixture memory() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        InMemoryDeliveryDraftRepository store = new InMemoryDeliveryDraftRepository();
        InMemoryCustomerReturnRepository returnStore = new InMemoryCustomerReturnRepository();
        InMemoryStockLedgerRepository ledger = new InMemoryStockLedgerRepository();
        StockLedgerService stock = new StockLedgerService(ledger);
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        CustomerReturnSourceService service = new CustomerReturnSourceService(
                store, new DocumentDeliveryStockPlaceLookup(documents), returnStore, documents, stock, unit);
        return new Fixture(bags, documents, store, returnStore, service, stock, ledger, unit);
    }

    private static InMemoryBusinessDocumentRepository documents() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(mother());
        return documents;
    }

    private static StockDimension dimension(StockStatus status) {
        return new StockDimension(OwnerType.CUSTOMER, 7L, 7L, "MD-20261030-1001", "MD-20261030-1001",
                "牙骨", "WH-01", status);
    }

    private static MotherOrderRecord mother() {
        return new MotherOrderRecord("MD-20261030-1001", 7L, 7L, "牙骨",
                null, null, null, null, null, null, null, "CUSTOMER", true, "WH-01",
                new BigDecimal("80"), "颗", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static OutboundOrderRecord outbound(String code) {
        return new OutboundOrderRecord(code, "MD-20261030-1001", "MD-20261030-1001", 7L, 7L, "牙骨",
                "WH-01", "WH-01", "2026-10-30", "交付", new BigDecimal("80"), "颗", new BigDecimal("5"),
                "", "", "", "", "DELIVERY", 1, "", "clerk-01", "", "", "SHIPPED", "STK-E04",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.parse("2026-10-30T12:00:00")));
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = StockRepositoryFixture.jdbcDataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{"V44__m3_command_request.sql", "V94__m3_customer_return_source.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }

    private record Prepared(String deliveryId, String outboundCode) {
    }

    private record Fixture(
            InMemoryActivityBagRepository bags,
            InMemoryBusinessDocumentRepository documents,
            InMemoryDeliveryDraftRepository store,
            InMemoryCustomerReturnRepository returnStore,
            CustomerReturnSourceService returns,
            StockLedgerService stock,
            InMemoryStockLedgerRepository ledger,
            MemoryBusinessWorkUnit unit
    ) {
    }
}
