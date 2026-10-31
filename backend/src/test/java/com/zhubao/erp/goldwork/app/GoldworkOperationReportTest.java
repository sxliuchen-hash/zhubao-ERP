package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOperationView;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOperationRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOperationRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldworkOperationReportTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 金工报工要照片且不写库存和工资() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m06-order-01", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "张三", "", "", "");
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        InMemoryGoldworkOperationRepository reports = new InMemoryGoldworkOperationRepository();
        GoldworkOperationService service = new GoldworkOperationService(orders, reports, documents, unit, guard);

        assertThatThrownBy(() -> service.report("m06-nophoto", keeper, order.goldworkCode(), "GOLD_CAST",
                null, null, "", "", "", false, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有有效的现场照片");
        documents.saveAttachment(photo("PH-BEAD", order.goldworkCode()));
        assertThatThrownBy(() -> service.report("m06-bead001", keeper, order.goldworkCode(), "POLISH_BEAD",
                null, null, "", "", "PH-BEAD", false, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工不使用珠料工序");
        assertThatThrownBy(() -> service.report("m06-wage001", keeper, order.goldworkCode(), "GOLD_CAST",
                null, null, "", "", "PH-BEAD", true, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("报工不生成工资");
        assertThatThrownBy(() -> service.report("m06-stock01", keeper, order.goldworkCode(), "GOLD_CAST",
                null, null, "", "", "PH-BEAD", false, true, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("报工不写库存");
        assertThatThrownBy(() -> service.report("m06-close01", keeper, order.goldworkCode(), "GOLD_CAST",
                null, null, "", "", "PH-BEAD", false, false, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("报工不结束在制");
        assertThat(reports.list(order.goldworkCode())).isEmpty();

        GoldworkOperationView first = service.report("m06-cast001", keeper, order.goldworkCode(), "GOLD_CAST",
                null, new BigDecimal("1.23456"), "克", "足金", "PH-BEAD", false, false, false);
        assertThat(first.stepId()).isEqualTo("GOLD_CAST");
        assertThat(first.stepLabel()).isEqualTo("倒模");
        assertThat(first.netWeight()).isEqualByComparingTo("1.23456");
        assertThat(first.postedToLedger()).isFalse();
        assertThat(first.payrollPosted()).isFalse();
        assertThat(first.workerName()).isEqualTo("金工");
        assertThat(first.reportedOn()).isEqualTo(LocalDate.now(ZONE).toString());
        documents.saveAttachment(photo("PH-PART", order.goldworkCode()));
        GoldworkOperationView second = service.report("m06-cast002", keeper, order.goldworkCode(), "GOLD_CAST",
                null, null, "", "", "PH-PART", false, false, false);
        assertThat(second.netWeight()).isNull();
        assertThat(second.payrollPosted()).isFalse();
        assertThatThrownBy(() -> service.report("m06-dup-001", keeper, order.goldworkCode(), "POLISH_GOLD",
                null, null, "", "", "PH-BEAD", false, false, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这张照片已经用于其他业务，不能当作本次拍摄");
        assertThat(orders.find(order.goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
        assertThat(reports.list(order.goldworkCode())).hasSize(2);
    }

    @Test
    void jdbc报工失败回滚且不记工资() throws Exception {
        JdbcDataSource dataSource = dataSource();
        var unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m06-jdbc-od", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "张三", "", "", "");
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(photo("PH-J1", order.goldworkCode()));
        documents.saveAttachment(photo("PH-J2", order.goldworkCode()));
        GoldworkOperationService service = new GoldworkOperationService(
                orders, new JdbcGoldworkOperationRepository(dataSource), documents, unit, guard);
        GoldworkOperationView saved = service.report("m06-jdbc-r1", keeper, order.goldworkCode(), "POLISH_GOLD",
                null, new BigDecimal("1.23456"), "克", "足金", "PH-J1", false, false, false);
        assertThat(saved.stepLabel()).isEqualTo("抛光（金）");
        assertThat(saved.netWeight().scale()).isGreaterThan(3);
        assertThat(saved.postedToLedger()).isFalse();
        assertThat(saved.payrollPosted()).isFalse();
        assertThat(count(dataSource, "select count(*) from gld_goldwork_operation")).isEqualByComparingTo("1");
        GoldworkOperationService failing = new GoldworkOperationService(
                orders, new JdbcGoldworkOperationRepository(dataSource), documents,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.report("m06-jdbc-r2", keeper, order.goldworkCode(), "GOLD_CAST",
                null, null, "", "", "PH-J2", false, false, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_operation")).isEqualByComparingTo("1");
        assertThat(orders.find(order.goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
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
            for (String file : new String[]{"V44__m3_command_request.sql", "V122__m3_goldwork_order.sql", "V127__m3_goldwork_operation.sql"}) {
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
