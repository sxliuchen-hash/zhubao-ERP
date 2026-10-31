package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.JdbcGoldworkOrderRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldworkOrderRouteTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 金工单使用抛光金且不依赖母单或打样() {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        GoldworkOrderService service = new GoldworkOrderService(
                customers, orders, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), guard);
        assertThatThrownBy(() -> open(service, "m01-mother1", customer.customerId(), "", "MD-20261029-0003", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工单不使用普通加工母单");
        assertThatThrownBy(() -> open(service, "m01-sample1", customer.customerId(), "", "", "DY-20261031-0001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工单不依赖打样单");
        assertThatThrownBy(() -> open(service, "m01-bead001", customer.customerId(), "POLISH_BEAD", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工抛光与珠料抛光不同");
        assertThat(orders.list()).isEmpty();

        GoldworkOrderView opened = open(service, "m01-open-001", customer.customerId(), "", "", "");
        assertThat(opened.goldworkCode()).startsWith("GJ-" + LocalDate.now(ZONE).toString().replace("-", "") + "-");
        assertThat(opened.businessReference()).isEqualTo("GOLDWORK|DOCUMENT|" + opened.goldworkCode());
        assertThat(BusinessTargetReference.fromCode(opened.goldworkCode()).kind()).isEqualTo(BusinessKind.GOLDWORK);
        assertThat(opened.routeStepIds()).contains("POLISH_GOLD").doesNotContain("POLISH_BEAD", "SAMPLE_MAKE", "SHAPE");
        assertThat(opened.routeStepLabels()).contains("抛光（金）").doesNotContain("抛光（珠）");
        assertThat(opened.statusLabel()).isEqualTo("进行中");
        GoldworkOrderView replay = open(service, "m01-open-001", customer.customerId(), "", "", "");
        assertThat(replay.goldworkCode()).isEqualTo(opened.goldworkCode());
        assertThat(orders.list()).hasSize(1);

        GoldworkOrderView custom = open(service, "m01-open-002", customer.customerId(), "GOLD_CAST,POLISH_GOLD", "", "");
        assertThat(custom.routeStepIds()).containsExactly("GOLD_CAST", "POLISH_GOLD");
        assertThat(guard.allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN)).doesNotContain(PermissionCode.GOLDWORK_MAINTAIN);
        assertThat(guard.allowedPermissionsFor(RoleCode.OPERATOR)).doesNotContain(PermissionCode.GOLDWORK_VIEW);
        OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        assertThatThrownBy(() -> service.open(
                "m01-deny-001", finance, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "张三", "", "", ""))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.list(finance, null)).isInstanceOf(PermissionDeniedException.class);
        assertThat(orders.list()).hasSize(2);
    }

    @Test
    void jdbc建单可回放且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcGoldworkOrderRepository orders = new JdbcGoldworkOrderRepository(dataSource);
        GoldworkOrderService service = new GoldworkOrderService(
                customers, orders, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        GoldworkOrderView opened = open(service, "m01-jdbc-001", customer.customerId(), "", "", "");
        GoldworkOrderView replay = open(service, "m01-jdbc-001", customer.customerId(), "", "", "");
        assertThat(replay.goldworkCode()).isEqualTo(opened.goldworkCode());
        assertThat(replay.routeStepIds()).contains("POLISH_GOLD").doesNotContain("POLISH_BEAD");
        assertThat(count(dataSource, "select count(*) from gld_goldwork_order")).isEqualByComparingTo("1");
        GoldworkOrderService failing = new GoldworkOrderService(
                customers, orders, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
            throw new IllegalStateException("失败");
        }), guard);
        assertThatThrownBy(() -> open(failing, "m01-jdbc-002", customer.customerId(), "", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from gld_goldwork_order")).isEqualByComparingTo("1");
    }

    private GoldworkOrderView open(GoldworkOrderService service, String requestId, long customerId, String route,
                                   String mother, String sampling) {
        return service.open(requestId, keeper, customerId, "CUSTOMER_SUPPLIED", "戒托", "张三", route, mother, sampling);
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
        var scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (int version : new int[]{44, 122}) {
                String sql = scripts.stream().filter(script -> script.version() == version).findFirst().orElseThrow().sql();
                for (String item : MigrationCatalog.statements(sql)) {
                    if (item.trim().regionMatches(true, 0, "create", 0, 6)) {
                        statement.execute(item.trim());
                    }
                }
            }
        }
        return dataSource;
    }
}
