package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetRole;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftSheet;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
import com.zhubao.erp.business.domain.DeliverySourceSelection;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryDeliveryDraftRepository;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.business.infra.JdbcDeliveryDraftRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryDraftTest {

    @Test
    void 同客户多目标汇总且草稿不改袋不扣库() {
        InMemoryActivityBagRepository stored = new InMemoryActivityBagRepository();
        CountingBags bags = new CountingBags(stored);
        InMemoryBusinessDocumentRepository documents = documents();
        InMemoryDeliveryDraftRepository drafts = new InMemoryDeliveryDraftRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        documents.saveMotherOrder(mother("MD-20261030-1001", 7L, "颗"));
        documents.saveChildWorkOrder(child("ZD-20261030-1002", "MD-20261030-1001", 7L, "件"));
        saveBag(stored, unit, "BAG-B", "MD-20261030-1001", new BigDecimal("120"), StockMeasurement.unknown(), "e01-bag-b01");
        saveBag(stored, unit, "BAG-A", "ZD-20261030-1002", new BigDecimal("30"),
                StockMeasurement.known(new BigDecimal("1.250")), "e01-bag-a01");
        DeliveryDraftService service = service(drafts, bags, documents, unit, bagId -> true);
        int mothers = documents.listMotherOrders().size();

        DeliveryDraftSheet sheet = service.open("e01-draft1", "clerk-01", 7L, "顺丰", "先发这批", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", ""),
                new DeliverySourceSelection("BAG-A", 1, "ZD-20261030-1002", "件")));

        assertThat(sheet.draft().deliveryId()).startsWith("FH-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        assertThat(sheet.draft().deliveryId()).doesNotStartWith("MD-");
        assertThat(sheet.draft().customerId()).isEqualTo(7L);
        assertThat(sheet.draft().businessKind()).isEqualTo(BusinessKind.PROCESSING);
        assertThat(sheet.draft().status()).isEqualTo(DeliveryDraftStatus.DRAFT);
        assertThat(sheet.draft().logisticsNote()).isEqualTo("顺丰");
        assertThat(sheet.draft().remark()).isEqualTo("先发这批");
        assertThat(sheet.draft().version()).isEqualTo(1);
        assertThat(sheet.lines()).hasSize(2);
        DeliveryDraftLine motherLine = sheet.lines().get(0);
        DeliveryDraftLine childLine = sheet.lines().get(1);
        assertThat(motherLine.lineNo()).isEqualTo(1);
        assertThat(motherLine.bagId()).isEqualTo("BAG-B");
        assertThat(motherLine.targetRole()).isEqualTo(BusinessTargetRole.MOTHER);
        assertThat(motherLine.targetCode()).isEqualTo("MD-20261030-1001");
        assertThat(motherLine.quantity()).isEqualByComparingTo("120");
        assertThat(motherLine.quantityUnit()).isEqualTo("颗");
        assertThat(motherLine.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(motherLine.weight().value()).isNull();
        assertThat(childLine.lineNo()).isEqualTo(2);
        assertThat(childLine.bagId()).isEqualTo("BAG-A");
        assertThat(childLine.targetRole()).isEqualTo(BusinessTargetRole.WORK_ORDER);
        assertThat(childLine.quantity()).isEqualByComparingTo("30");
        assertThat(childLine.quantityUnit()).isEqualTo("件");
        assertThat(childLine.weight().value()).isEqualByComparingTo("1.250");
        assertThat(bags.saves).isZero();
        ActivityBag motherBag = stored.find("BAG-B").orElseThrow();
        assertThat(motherBag.quantity()).isEqualByComparingTo("120");
        assertThat(motherBag.closed()).isFalse();
        assertThat(motherBag.version()).isEqualTo(1);
        assertThat(motherBag.weight().value()).isNull();
        assertThat(stored.find("BAG-A").orElseThrow().quantity()).isEqualByComparingTo("30");
        assertThat(documents.listMotherOrders()).hasSize(mothers);
        assertThat(documents.listOutboundOrders()).isEmpty();
        assertThat(documents.listAllChildWorkOrders()).hasSize(1);
    }

    @Test
    void 混客户混种类和未核定金工都不写成普通工单() {
        InMemoryActivityBagRepository stored = new InMemoryActivityBagRepository();
        CountingBags bags = new CountingBags(stored);
        InMemoryBusinessDocumentRepository documents = documents();
        InMemoryDeliveryDraftRepository drafts = new InMemoryDeliveryDraftRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        documents.saveMotherOrder(mother("MD-20261030-1001", 7L, "颗"));
        documents.saveMotherOrder(mother("MD-20261030-1003", 8L, "颗"));
        saveBag(stored, unit, "BAG-B", "MD-20261030-1001", new BigDecimal("120"), StockMeasurement.unknown(), "e01-bag-b02");
        saveBag(stored, unit, "BAG-C", "MD-20261030-1003", new BigDecimal("8"), StockMeasurement.unknown(), "e01-bag-c02");
        saveBag(stored, unit, "BAG-OUT", "MD-20261030-1001", new BigDecimal("10"), StockMeasurement.unknown(), "e01-bag-out");
        stored.save(new ActivityBag("BAG-CLOSED", "MD-20261030-1001", "牙骨", "REQ-1", "LINE-1", "", true,
                new BigDecimal("4"), StockMeasurement.unknown(), 1), 0, null);
        saveBag(stored, unit, "BAG-DY", "DY-20261030-0001", new BigDecimal("2"), StockMeasurement.unknown(), "e01-bag-dy1");
        saveBag(stored, unit, "BAG-GJ", "GJ-20261030-0001", new BigDecimal("1.5"), StockMeasurement.unknown(), "e01-bag-gj1");
        saveBag(stored, unit, "BAG-MISS", "MD-20261030-1999", new BigDecimal("6"), StockMeasurement.unknown(), "e01-bag-mis");
        DeliveryDraftService service = service(drafts, bags, documents, unit, bagId -> !"BAG-OUT".equals(bagId));
        int mothers = documents.listMotherOrders().size();

        assertThatThrownBy(() -> service.open("e01-mixcus", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-C", 1, "MD-20261030-1003", "颗"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能把不同客户放在同一张交付单");
        assertThatThrownBy(() -> service.open("e01-mixknd", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗"),
                new DeliverySourceSelection("BAG-DY", 1, "DY-20261030-0001", "颗"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("跨种类混单尚未确认，不能放在同一张交付单");
        assertThatThrownBy(() -> service.open("e01-gold01", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-GJ", 1, "GJ-20261030-0001", "克"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工计量精度尚未核定，不能按现有三位库存截断后入账");
        assertThatThrownBy(() -> service.open("e01-unit01", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "克"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数量单位与加工目标不一致");
        assertThatThrownBy(() -> service.open("e01-out001", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-OUT", 1, "MD-20261030-1001", "颗"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协未回的份额不能当厂内可发");
        assertThatThrownBy(() -> service.open("e01-closed", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-CLOSED", 1, "MD-20261030-1001", "颗"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已关闭的袋不能进入交付草稿");
        assertThatThrownBy(() -> service.open("e01-miss01", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-MISS", 1, "MD-20261030-1999", "颗"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("加工目标不存在");
        assertThatThrownBy(() -> service.open("e01-dupbag", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗"),
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一袋不能在一张交付草稿里重复选择");
        assertThatThrownBy(() -> service.open("e01-empty1", "clerk-01", 7L, "", "", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("交付草稿至少选择一袋");
        assertThatThrownBy(() -> new DeliveryDraftLine(
                "FH-20261030-0001", 1, "BAG-DY", 1, BusinessKind.PROCESSING, BusinessTargetRole.MOTHER,
                "DY-20261030-0001", BigDecimal.ONE, "颗", StockMeasurement.unknown(), "牙骨", "", "REQ-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("业务引用种类与单号不一致");

        DeliveryDraftSheet sampling = service.open("e01-sample", "clerk-01", 9L, "", "样品", List.of(
                new DeliverySourceSelection("BAG-DY", 1, "DY-20261030-0001", "颗")));
        assertThat(sampling.draft().businessKind()).isEqualTo(BusinessKind.SAMPLING);
        assertThat(sampling.draft().customerId()).isEqualTo(9L);
        assertThat(sampling.lines()).singleElement().satisfies(line -> {
            assertThat(line.targetCode()).isEqualTo("DY-20261030-0001");
            assertThat(line.targetRole()).isEqualTo(BusinessTargetRole.DOCUMENT);
            assertThat(line.businessKind()).isEqualTo(BusinessKind.SAMPLING);
            assertThat(line.quantity()).isEqualByComparingTo("2");
            assertThat(line.weight().value()).isNull();
        });
        assertThat(documents.findMotherOrderByCode("DY-20261030-0001")).isEmpty();
        assertThat(documents.findMotherOrderByCode("GJ-20261030-0001")).isEmpty();
        assertThat(documents.findChildWorkOrderByCode("DY-20261030-0001")).isEmpty();
        assertThat(documents.listMotherOrders()).hasSize(mothers);
        assertThat(documents.listOutboundOrders()).isEmpty();
        assertThat(drafts.findByRequest("e01-gold01")).isEmpty();
        assertThat(drafts.findByRequest("e01-mixknd")).isEmpty();
        assertThat(bags.saves).isZero();
        assertThat(stored.find("BAG-GJ").orElseThrow().targetCode()).isEqualTo("GJ-20261030-0001");
        assertThat(stored.find("BAG-GJ").orElseThrow().quantity()).isEqualByComparingTo("1.5");
        assertThat(stored.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("120");
    }

    @Test
    void 原请求回放且失败不留草稿() {
        InMemoryActivityBagRepository stored = new InMemoryActivityBagRepository();
        InMemoryBusinessDocumentRepository documents = documents();
        InMemoryDeliveryDraftRepository drafts = new InMemoryDeliveryDraftRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        documents.saveMotherOrder(mother("MD-20261030-1001", 7L, "颗"));
        saveBag(stored, unit, "BAG-B", "MD-20261030-1001", new BigDecimal("120"), StockMeasurement.unknown(), "e01-bag-b03");
        DeliveryDraftService service = service(drafts, stored, documents, unit, bagId -> true);
        List<DeliverySourceSelection> lines = List.of(new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗"));

        DeliveryDraftSheet first = service.open("e01-replay", "clerk-01", 7L, "顺丰", "", lines);
        DeliveryDraftSheet replay = service.open("e01-replay", "clerk-01", 7L, "顺丰", "", lines);
        assertThat(replay.draft().deliveryId()).isEqualTo(first.draft().deliveryId());
        assertThat(drafts.listIds(null)).hasSize(1);
        assertThatThrownBy(() -> service.open("e01-replay", "clerk-01", 7L, "改物流", "", lines))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThat(drafts.listIds(null)).hasSize(1);
        assertThat(stored.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("120");

        DeliveryDraftService failing = service(drafts, stored, documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }), bagId -> true);
        assertThatThrownBy(() -> failing.open("e01-fail01", "clerk-01", 7L, "", "", lines))
                .isInstanceOf(IllegalStateException.class);
        assertThat(drafts.findByRequest("e01-fail01")).isEmpty();
        assertThat(drafts.listIds(null)).hasSize(1);
        assertThat(stored.find("BAG-B").orElseThrow().closed()).isFalse();
        assertThat(documents.listOutboundOrders()).isEmpty();
    }

    @Test
    void jdbc往返且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcActivityBagRepository bags = new JdbcActivityBagRepository(dataSource);
        JdbcDeliveryDraftRepository drafts = new JdbcDeliveryDraftRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        InMemoryBusinessDocumentRepository documents = documents();
        documents.saveMotherOrder(mother("MD-20261030-1001", 7L, "颗"));
        documents.saveChildWorkOrder(child("ZD-20261030-1002", "MD-20261030-1001", 7L, "件"));
        saveBag(bags, unit, "BAG-B", "MD-20261030-1001", new BigDecimal("120"), StockMeasurement.unknown(), "e01-jbag-b1");
        saveBag(bags, unit, "BAG-A", "ZD-20261030-1002", new BigDecimal("30"),
                StockMeasurement.known(new BigDecimal("1.250")), "e01-jbag-a1");
        CountingBags guarded = new CountingBags(bags);
        DeliveryDraftService service = service(drafts, guarded, documents, unit, bagId -> true);

        DeliveryDraftSheet sheet = service.open("e01-jdbc01", "clerk-01", 7L, "顺丰", "先发这批", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", ""),
                new DeliverySourceSelection("BAG-A", 1, "ZD-20261030-1002", "")));
        DeliveryDraftSheet replay = service.open("e01-jdbc01", "clerk-01", 7L, "顺丰", "先发这批", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", ""),
                new DeliverySourceSelection("BAG-A", 1, "ZD-20261030-1002", "")));
        DeliveryDraft stored = new JdbcDeliveryDraftRepository(dataSource).find(sheet.draft().deliveryId()).orElseThrow();
        List<DeliveryDraftLine> lines = new JdbcDeliveryDraftRepository(dataSource).lines(stored.deliveryId());

        assertThat(replay.draft().deliveryId()).isEqualTo(sheet.draft().deliveryId());
        assertThat(stored.businessKind()).isEqualTo(BusinessKind.PROCESSING);
        assertThat(stored.customerId()).isEqualTo(7L);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(lines.get(0).weight().value()).isNull();
        assertThat(lines.get(0).quantity()).isEqualByComparingTo("120");
        assertThat(lines.get(1).weight().value()).isEqualByComparingTo("1.250");
        assertThat(lines.get(1).quantityUnit()).isEqualTo("件");
        assertThat(guarded.saves).isZero();
        assertThat(bags.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("120");
        assertThat(bags.find("BAG-B").orElseThrow().weight().value()).isNull();
        assertThat(documents.listOutboundOrders()).isEmpty();
        assertThat(documents.listMotherOrders()).hasSize(1);

        DeliveryDraftService failing = service(drafts, bags, documents, new JdbcBusinessWorkUnit(
                dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
            throw new IllegalStateException("失败");
        }), bagId -> true);
        assertThatThrownBy(() -> failing.open("e01-jfail1", "clerk-01", 7L, "", "", List.of(
                new DeliverySourceSelection("BAG-B", 1, "MD-20261030-1001", "颗"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(drafts.findByRequest("e01-jfail1")).isEmpty();
        assertThat(bags.find("BAG-B").orElseThrow().version()).isEqualTo(1);
        assertThat(bags.find("BAG-B").orElseThrow().quantity()).isEqualByComparingTo("120");
    }

    private static DeliveryDraftService service(DeliveryDraftRepository drafts, ActivityBagRepository bags,
                                                InMemoryBusinessDocumentRepository documents,
                                                com.zhubao.erp.work.app.BusinessWorkUnit unit,
                                                java.util.function.Predicate<String> inHouse) {
        return new DeliveryDraftService(drafts, bags, new DocumentProcessingTargetCustomer(documents),
                inHouse::test, unit);
    }

    private static InMemoryBusinessDocumentRepository documents() {
        return new InMemoryBusinessDocumentRepository();
    }

    private static void saveBag(ActivityBagRepository bags, com.zhubao.erp.work.app.BusinessWorkUnit unit, String bagId,
                                String target, BigDecimal quantity, StockMeasurement.Weight weight, String requestId) {
        ActivityBag bag = new ActivityBag(bagId, target, "牙骨", "REQ-1", "LINE-1", "", false, quantity, weight, 1);
        unit.execute(new CommandCall(requestId, "clerk-01", "SAVE_BAG", "BAG", bagId, bagId), session -> {
            bags.save(bag, 0, session);
            return bag.id();
        });
    }

    private static MotherOrderRecord mother(String code, long customerId, String unit) {
        return new MotherOrderRecord(code, customerId, customerId, "牙骨",
                null, null, null, null, null, null, null, null, false, null,
                new BigDecimal("120"), unit, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static ChildWorkOrderRecord child(String code, String motherCode, long customerId, String unit) {
        return new ChildWorkOrderRecord(code, motherCode, customerId, "客户", "要求", null,
                "牙骨", "货品", new BigDecimal("30"), unit, null, "WH", "", null, null, "clerk", null);
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V81__m3_activity_bag.sql", "V92__m3_delivery_draft.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }

    private static final class CountingBags implements ActivityBagRepository {
        private final ActivityBagRepository inner;
        private int saves;

        private CountingBags(ActivityBagRepository inner) {
            this.inner = inner;
        }

        @Override
        public void save(ActivityBag bag, int expectedVersion, WorkSession session) {
            saves++;
            inner.save(bag, expectedVersion, session);
        }

        @Override
        public Optional<ActivityBag> find(String id) {
            return inner.find(id);
        }

        @Override
        public Optional<ActivityBag> lock(String id, WorkSession session) {
            return inner.lock(id, session);
        }

        @Override
        public List<ActivityBag> listByTarget(String targetCode) {
            return inner.listByTarget(targetCode);
        }
    }
}
