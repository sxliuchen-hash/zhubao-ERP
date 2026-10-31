package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.goldwork.domain.GoldworkDeliveryView;
import com.zhubao.erp.goldwork.domain.GoldworkOperationView;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.domain.GoldworkQualityView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkDeliveryRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOperationRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkQualityRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkDeliveryRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOperationRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkQualityRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldworkDeliveryBillingTest {

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 交付不按克数乘金价也不写应收() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        Fixture fixture = memoryFixture(unit, "m08-order-01");
        InMemoryGoldworkDeliveryRepository deliveries = new InMemoryGoldworkDeliveryRepository();
        GoldworkDeliveryService service = new GoldworkDeliveryService(
                fixture.orders(), fixture.qualities(), deliveries, unit, guard);
        assertThatThrownBy(() -> deliver(service, "m08-price01", fixture.order().goldworkCode(), fixture.released().qualityId(),
                new BigDecimal("520"), null, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能按克数乘金价计费");
        assertThatThrownBy(() -> deliver(service, "m08-amount1", fixture.order().goldworkCode(), fixture.released().qualityId(),
                null, new BigDecimal("80"), false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工收费时点尚未核定");
        assertThatThrownBy(() -> deliver(service, "m08-ship001", fixture.order().goldworkCode(), fixture.released().qualityId(),
                null, null, true, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工交付不写普通发货");
        assertThatThrownBy(() -> deliver(service, "m08-sales01", fixture.order().goldworkCode(), fixture.released().qualityId(),
                null, null, false, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户来料不记销售收入");
        assertThatThrownBy(() -> deliver(service, "m08-pend001", fixture.order().goldworkCode(), fixture.pending().qualityId(),
                null, null, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有放行份额可以交付");
        assertThat(deliveries.list(fixture.order().goldworkCode())).isEmpty();

        GoldworkDeliveryView saved = deliver(service, "m08-del-001", fixture.order().goldworkCode(), fixture.released().qualityId(),
                null, null, false, false);
        assertThat(saved.feeState()).isEqualTo("UNCONFIRMED");
        assertThat(saved.receivablePosted()).isFalse();
        assertThat(saved.salesPosted()).isFalse();
        assertThat(saved.ordinaryShipment()).isFalse();
        assertThat(saved.netWeight()).isEqualByComparingTo("0.23456");
        assertThat(saved.purity()).isEqualTo("足金");
        assertThatThrownBy(() -> deliver(service, "m08-del-002", fixture.order().goldworkCode(), fixture.released().qualityId(),
                null, null, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一放行份额不能重复交付");
        assertThat(fixture.orders().find(fixture.order().goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
    }

    @Test
    void jdbc交付失败回滚且不写应收() throws Exception {
        JdbcDataSource dataSource = dataSource();
        var unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        JdbcGoldworkOperationRepository reports = new JdbcGoldworkOperationRepository(dataSource);
        JdbcGoldworkQualityRepository qualities = new JdbcGoldworkQualityRepository(dataSource);
        GoldworkQualityView released = release(orders, reports, qualities, unit, "m08-jdbc");
        GoldworkDeliveryService service = new GoldworkDeliveryService(
                orders, qualities, new JdbcGoldworkDeliveryRepository(dataSource), unit, guard);
        GoldworkDeliveryView saved = deliver(service, "m08-jdbc-ok", released.goldworkCode(), released.qualityId(),
                null, null, false, false);
        assertThat(saved.feeState()).isEqualTo("UNCONFIRMED");
        assertThat(saved.receivablePosted()).isFalse();
        assertThat(saved.salesPosted()).isFalse();
        assertThat(saved.netWeight().scale()).isGreaterThan(3);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_delivery")).isEqualByComparingTo("1");
        GoldworkDeliveryService failing = new GoldworkDeliveryService(
                orders, qualities, new JdbcGoldworkDeliveryRepository(dataSource),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        GoldworkQualityView another = release(orders, reports, qualities, unit, "m08-jdbc2");
        assertThatThrownBy(() -> failing.deliver("m08-jdbc-bad", keeper, another.goldworkCode(), another.qualityId(),
                null, new BigDecimal("0.1"), "克", null, null, false, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_delivery")).isEqualByComparingTo("1");
    }

    private Fixture memoryFixture(MemoryBusinessWorkUnit unit, String orderRequest) {
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryGoldworkOperationRepository reports = new InMemoryGoldworkOperationRepository();
        InMemoryGoldworkQualityRepository qualities = new InMemoryGoldworkQualityRepository();
        GoldworkOrderView order = open(orders, unit, orderRequest);
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(photo("PH-1", order.goldworkCode()));
        documents.saveAttachment(photo("PH-2", order.goldworkCode()));
        GoldworkOperationService operations = new GoldworkOperationService(orders, reports, documents, unit, guard);
        GoldworkOperationView report = operations.report(orderRequest + "-rp", keeper, order.goldworkCode(), "GOLD_CAST",
                null, new BigDecimal("1.2"), "克", "足金", "PH-1", false, false, false);
        GoldworkOperationView other = operations.report(orderRequest + "-r2", keeper, order.goldworkCode(), "POLISH_GOLD",
                null, new BigDecimal("0.4"), "克", "足金", "PH-2", false, false, false);
        GoldworkQualityService qualityService = new GoldworkQualityService(orders, reports, qualities, unit, guard);
        GoldworkQualityView released = qualityService.decide(orderRequest + "-ql", keeper, order.goldworkCode(), report.reportId(),
                "", "RELEASE", null, new BigDecimal("0.23456"), "克", "", "", false, false, false);
        GoldworkQualityView pending = qualityService.decide(orderRequest + "-pd", keeper, order.goldworkCode(), other.reportId(),
                "", "PENDING", null, new BigDecimal("0.1"), "克", "", "待核", false, false, false);
        return new Fixture(orders, qualities, order, released, pending);
    }

    private GoldworkQualityView release(JdbcGoldworkOrderRepository orders, JdbcGoldworkOperationRepository reports,
                                        JdbcGoldworkQualityRepository qualities, BusinessWorkUnit unit, String prefix) {
        GoldworkOrderView order = open(orders, unit, prefix + "-od");
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(photo(prefix + "-ph", order.goldworkCode()));
        GoldworkOperationView report = new GoldworkOperationService(orders, reports, documents, unit, guard).report(
                prefix + "-rp", keeper, order.goldworkCode(), "GOLD_CAST",
                null, new BigDecimal("1.2"), "克", "足金", prefix + "-ph", false, false, false);
        return new GoldworkQualityService(orders, reports, qualities, unit, guard).decide(
                prefix + "-ql", keeper, order.goldworkCode(), report.reportId(),
                "", "RELEASE", null, new BigDecimal("0.23456"), "克", "", "", false, false, false);
    }

    private GoldworkOrderView open(com.zhubao.erp.goldwork.domain.GoldworkOrderRepository orders, BusinessWorkUnit unit, String requestId) {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        return new GoldworkOrderService(customers, orders, unit, guard).open(
                requestId, keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "张三", "", "", "");
    }

    private GoldworkDeliveryView deliver(GoldworkDeliveryService service, String requestId, String goldworkCode, String qualityId,
                                         BigDecimal goldPrice, BigDecimal amount, boolean ship, boolean sales) {
        return service.deliver(requestId, keeper, goldworkCode, qualityId, null, new BigDecimal("0.23456"), "克",
                goldPrice, amount, ship, sales);
    }

    private static EvidenceAttachment photo(String code, String businessCode) {
        return new EvidenceAttachment(code, "DOC-" + code, "GOLDWORK", businessCode, code + ".jpg", "image/jpeg",
                "mem", "LOCAL", "UPLOAD", "可用", "金工", "sum", "2026-10-31T10:00:00", "", "", "GOLDWORK");
    }

    private static BigDecimal count(JdbcDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getBigDecimal(1);
        }
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V122__m3_goldwork_order.sql",
                    "V127__m3_goldwork_operation.sql", "V128__m3_goldwork_quality.sql",
                    "V129__m3_goldwork_delivery.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    String trimmed = item.trim();
                    if (trimmed.regionMatches(true, 0, "create", 0, 6) || trimmed.regionMatches(true, 0, "alter", 0, 5)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }

    private record Fixture(
            InMemoryGoldworkOrderRepository orders,
            InMemoryGoldworkQualityRepository qualities,
            GoldworkOrderView order,
            GoldworkQualityView released,
            GoldworkQualityView pending
    ) {
    }
}
