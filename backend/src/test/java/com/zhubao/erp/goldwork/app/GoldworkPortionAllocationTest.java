package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.goldwork.domain.*;
import com.zhubao.erp.goldwork.infra.*;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.*;
import com.zhubao.erp.work.infra.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class GoldworkPortionAllocationTest {
    private final OperatorContext actor = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 放行返工和已交付共享原份额不能重复使用(boolean jdbc) throws Exception {
        Fixture f = fixture(jdbc);
        var service = new GoldworkQualityService(f.orders, f.reports, f.qualities, f.unit, guard, f.deliveries);
        var shipping = new GoldworkDeliveryService(f.orders, f.qualities, f.deliveries, f.unit, guard, f.reports);
        assertThatThrownBy(() -> decide(service, f, "too-big-release", "", "RELEASE", "100", "克")).hasMessageContaining("超过");
        assertThatThrownBy(() -> decide(service, f, "wrong-unit-release", "", "RELEASE", "0.1", "两")).hasMessageContaining("单位");
        var released = decide(service, f, "proper-release", "", "RELEASE", "0.5", "克");
        var rework = decide(service, f, "proper-rework", "", "REWORK", "0.3", "克");
        assertThatThrownBy(() -> decide(service, f, "overbooked-pending", "", "PENDING", "0.3", "克")).hasMessageContaining("超过");
        var fixed = decide(service, f, "reworked-release", rework.qualityId(), "RELEASE", "0.3", "克");
        assertThatThrownBy(() -> decide(service, f, "rework-again", rework.qualityId(), "RELEASE", "0.1", "克")).hasMessageContaining("超过");
        var first = ship(shipping, f, "partial-shipment", released.qualityId(), "0.2", "克");
        assertThat(ship(shipping, f, "partial-shipment", released.qualityId(), "0.2", "克").deliveryId()).isEqualTo(first.deliveryId());
        assertThatThrownBy(() -> ship(shipping, f, "wrong-shipment-unit", released.qualityId(), "0.1", "两")).hasMessageContaining("单位");
        var diverted = decide(service, f, "unshipped-rework", released.qualityId(), "REWORK", "0.1", "克");
        ship(shipping, f, "remaining-shipment", released.qualityId(), "0.2", "克");
        assertThatThrownBy(() -> ship(shipping, f, "ship-consumed-again", released.qualityId(), "0.1", "克")).hasMessageContaining("超过");
        assertThatThrownBy(() -> decide(service, f, "release-consumed-again", released.qualityId(), "RELEASE", "0.1", "克")).hasMessageContaining("超过");
        var repaired = decide(service, f, "repair-release", diverted.qualityId(), "RELEASE", "0.1", "克");
        ship(shipping, f, "repaired-shipment", repaired.qualityId(), "0.1", "克");
        ship(shipping, f, "fixed-shipment", fixed.qualityId(), "0.3", "克");
        assertThat(f.deliveries.list(f.order).stream().map(GoldworkDelivery::netWeight).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0.8");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 并发分次交付不超过放行份额(boolean jdbc) throws Exception {
        Fixture f = fixture(jdbc);
        var quality = new GoldworkQualityService(f.orders, f.reports, f.qualities, f.unit, guard, f.deliveries);
        var shipping = new GoldworkDeliveryService(f.orders, f.qualities, f.deliveries, f.unit, guard, f.reports);
        var released = decide(quality, f, "race-source-release", "", "RELEASE", "0.5", "克");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String request = "race-gold-shipment-" + i;
                attempts.add(pool.submit(() -> {
                    start.await();
                    try { ship(shipping, f, request, released.qualityId(), "0.3", "克"); return true; }
                    catch (BusinessException ex) { assertThat(ex).hasMessageContaining("超过"); return false; }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var attempt : attempts) if (attempt.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(f.deliveries.list(f.order)).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    private GoldworkQualityView decide(GoldworkQualityService service, Fixture f, String request, String prior, String outcome, String weight, String unit) {
        return service.decide(request, actor, f.order, f.report, prior, outcome, null, new BigDecimal(weight), unit, "", "本次检查", false, false, false);
    }
    private GoldworkDeliveryView ship(GoldworkDeliveryService service, Fixture f, String request, String quality, String weight, String unit) {
        return service.deliver(request, actor, f.order, quality, null, new BigDecimal(weight), unit, null, null, false, false);
    }

    private Fixture fixture(boolean jdbc) throws Exception {
        JdbcDataSource ds = null;
        if (jdbc) {
            ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
            try (var c = ds.getConnection(); var s = c.createStatement()) {
                for (String file : new String[]{"V44__m3_command_request.sql", "V122__m3_goldwork_order.sql", "V127__m3_goldwork_operation.sql",
                        "V128__m3_goldwork_quality.sql", "V129__m3_goldwork_delivery.sql", "V140__m3_goldwork_partial_delivery.sql"}) {
                    for (String sql : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql", file)))) {
                        String lower = sql.stripLeading().toLowerCase(java.util.Locale.ROOT);
                        if (!lower.startsWith("create") && !lower.startsWith("alter")) continue;
                        // H2外键持有原唯一索引，先重建该外键才能得到与MySQL删除索引相同的结构。
                        if (lower.contains("drop index uk_gld_delivery_quality")) {
                            s.execute("alter table gld_goldwork_delivery drop constraint fk_gld_delivery_quality");
                            s.execute(sql.replace("drop index uk_gld_delivery_quality", "drop constraint uk_gld_delivery_quality"));
                            s.execute("alter table gld_goldwork_delivery add constraint fk_gld_delivery_quality foreign key (quality_id) references gld_goldwork_quality(quality_id)");
                        } else s.execute(sql);
                    }
                }
            }
            assertThat(new com.zhubao.erp.ops.infra.JdbcSchemaInspector(ds).matches(new com.zhubao.erp.ops.domain.SchemaProbe(
                    com.zhubao.erp.ops.domain.SchemaProbe.Kind.INDEX, "gld_goldwork_delivery", "idx_gld_delivery_quality", ""))).isTrue();
        }
        GoldworkOrderRepository orders = jdbc ? new JdbcGoldworkOrderRepository(ds) : new InMemoryGoldworkOrderRepository();
        GoldworkOperationRepository reports = jdbc ? new JdbcGoldworkOperationRepository(ds) : new InMemoryGoldworkOperationRepository();
        GoldworkQualityRepository qualities = jdbc ? new JdbcGoldworkQualityRepository(ds) : new InMemoryGoldworkQualityRepository();
        GoldworkDeliveryRepository deliveries = jdbc ? new JdbcGoldworkDeliveryRepository(ds) : new InMemoryGoldworkDeliveryRepository();
        BusinessWorkUnit unit = jdbc ? new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds)) : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var customers = new InMemoryMasterDataRepository();
        var customer = customers.saveCustomer(new CustomerProfile(0, "", "金工客户", "", "", "", "", true));
        var order = new GoldworkOrderService(customers, orders, unit, guard).open("allocation-gold-order", actor, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "金工", "", "", "");
        var documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(new EvidenceAttachment("PHOTO", "DOC", "GOLDWORK", order.goldworkCode(), "photo.jpg", "image/jpeg", "mem", "LOCAL", "UPLOAD", "已上传", "金工", "checksum", "2026-11-01", "", "", "GOLDWORK"));
        var report = new GoldworkOperationService(orders, reports, documents, unit, guard).report("allocation-gold-report", actor, order.goldworkCode(), "GOLD_CAST",
                null, BigDecimal.ONE, "克", "足金", "PHOTO", false, false, false);
        return new Fixture(orders, reports, qualities, deliveries, unit, order.goldworkCode(), report.reportId());
    }
    private record Fixture(GoldworkOrderRepository orders, GoldworkOperationRepository reports, GoldworkQualityRepository qualities,
                           GoldworkDeliveryRepository deliveries, BusinessWorkUnit unit, String order, String report) {}
}
