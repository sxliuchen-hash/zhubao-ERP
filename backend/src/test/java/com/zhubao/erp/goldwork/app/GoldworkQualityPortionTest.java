package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOperationView;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.domain.GoldworkQualityView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOperationRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkQualityRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOperationRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkQualityRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
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

class GoldworkQualityPortionTest {

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 返工只记本份额且不改成色和收费() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m07-order-01", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "张三", "", "", "");
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(photo("PH-A", order.goldworkCode()));
        documents.saveAttachment(photo("PH-B", order.goldworkCode()));
        InMemoryGoldworkOperationRepository reports = new InMemoryGoldworkOperationRepository();
        GoldworkOperationService operations = new GoldworkOperationService(orders, reports, documents, unit, guard);
        GoldworkOperationView first = operations.report("m07-rep-001", keeper, order.goldworkCode(), "GOLD_CAST",
                null, new BigDecimal("1.20000"), "克", "足金", "PH-A", false, false, false);
        GoldworkOperationView second = operations.report("m07-rep-002", keeper, order.goldworkCode(), "POLISH_GOLD",
                null, new BigDecimal("0.80000"), "克", "足金", "PH-B", false, false, false);
        InMemoryGoldworkQualityRepository qualities = new InMemoryGoldworkQualityRepository();
        GoldworkQualityService service = new GoldworkQualityService(orders, reports, qualities, unit, guard);

        assertThatThrownBy(() -> decide(service, "m07-bead001", order.goldworkCode(), first.reportId(), "",
                "RELEASE", new BigDecimal("0.2"), "", "", true, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不使用珠料质检默认条件");
        assertThatThrownBy(() -> decide(service, "m07-purity1", order.goldworkCode(), first.reportId(), "",
                "RELEASE", new BigDecimal("0.2"), "18K", "", false, true, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("返工不改成色");
        assertThatThrownBy(() -> decide(service, "m07-fee-001", order.goldworkCode(), first.reportId(), "",
                "RELEASE", new BigDecimal("0.2"), "", "", false, false, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("返工不改收费");
        assertThatThrownBy(() -> decide(service, "m07-empty01", order.goldworkCode(), first.reportId(), "",
                "RELEASE", null, "", "", false, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写本次检查份额");
        assertThat(qualities.list(order.goldworkCode())).isEmpty();

        GoldworkQualityView released = decide(service, "m07-rel-001", order.goldworkCode(), first.reportId(), "",
                "RELEASE", new BigDecimal("0.23456"), "", "", false, false, false);
        assertThat(released.roundNo()).isEqualTo(1);
        assertThat(released.purity()).isEqualTo("足金");
        assertThat(released.purityChanged()).isFalse();
        assertThat(released.feeChanged()).isFalse();
        assertThat(released.netWeight()).isEqualByComparingTo("0.23456");
        assertThat(reports.find(first.reportId()).orElseThrow().purity()).isEqualTo("足金");
        assertThat(reports.find(first.reportId()).orElseThrow().netWeight()).isEqualByComparingTo("1.20000");

        GoldworkQualityView other = decide(service, "m07-rel-002", order.goldworkCode(), second.reportId(), "",
                "PENDING", new BigDecimal("0.1"), "", "边角待核", false, false, false);
        assertThatThrownBy(() -> decide(service, "m07-cross01", order.goldworkCode(), first.reportId(), other.qualityId(),
                "REWORK", new BigDecimal("0.1"), "", "串到另一份额", false, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("返工只影响本份额");
        GoldworkQualityView rework = decide(service, "m07-rework1", order.goldworkCode(), first.reportId(), released.qualityId(),
                "REWORK", new BigDecimal("0.23456"), "", "砂眼", false, false, false);
        assertThat(rework.roundNo()).isEqualTo(2);
        assertThat(rework.priorQualityId()).isEqualTo(released.qualityId());
        assertThat(qualities.find(released.qualityId()).orElseThrow().outcome().name()).isEqualTo("RELEASE");
        assertThat(qualities.find(other.qualityId()).orElseThrow().outcome().name()).isEqualTo("PENDING");
        assertThat(orders.find(order.goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
    }

    @Test
    void jdbc质量失败回滚且不改报工成色() throws Exception {
        JdbcDataSource dataSource = dataSource();
        var unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m07-jdbc-od", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "张三", "", "", "");
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(photo("PH-J", order.goldworkCode()));
        JdbcGoldworkOperationRepository reports = new JdbcGoldworkOperationRepository(dataSource);
        GoldworkOperationView report = new GoldworkOperationService(orders, reports, documents, unit, guard).report(
                "m07-jdbc-rp", keeper, order.goldworkCode(), "GOLD_CAST",
                null, new BigDecimal("1.2"), "克", "足金", "PH-J", false, false, false);
        GoldworkQualityService service = new GoldworkQualityService(
                orders, reports, new JdbcGoldworkQualityRepository(dataSource), unit, guard);
        GoldworkQualityView saved = decide(service, "m07-jdbc-ok", order.goldworkCode(), report.reportId(), "",
                "RELEASE", new BigDecimal("0.23456"), "", "", false, false, false);
        assertThat(saved.netWeight().scale()).isGreaterThan(3);
        assertThat(saved.purityChanged()).isFalse();
        assertThat(saved.feeChanged()).isFalse();
        assertThat(reports.find(report.reportId()).orElseThrow().purity()).isEqualTo("足金");
        assertThat(count(dataSource, "select count(*) from gld_goldwork_quality")).isEqualByComparingTo("1");
        GoldworkQualityService failing = new GoldworkQualityService(
                orders, reports, new JdbcGoldworkQualityRepository(dataSource),
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> decide(failing, "m07-jdbc-bad", order.goldworkCode(), report.reportId(), saved.qualityId(),
                "REWORK", new BigDecimal("0.1"), "", "砂眼", false, false, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_quality")).isEqualByComparingTo("1");
    }

    private GoldworkQualityView decide(GoldworkQualityService service, String requestId, String goldworkCode, String reportId,
                                       String priorId, String outcome, BigDecimal net, String purity, String note,
                                       boolean beadDefault, boolean changePurity, boolean changeFee) {
        return service.decide(requestId, keeper, goldworkCode, reportId, priorId, outcome,
                null, net, net == null ? "" : "克", purity, note, beadDefault, changePurity, changeFee);
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
                    "V127__m3_goldwork_operation.sql", "V128__m3_goldwork_quality.sql"}) {
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
}
