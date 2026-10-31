package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.domain.SamplingVersionView;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingVersionRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOrderRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingVersionRepository;
import com.zhubao.erp.sampling.web.SamplingVersionController;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SamplingVersionConfirmationTest {

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 已确认版本不被新版本覆盖且无文件也能确认() {
        Fixture fixture = memory();
        SamplingOrderView order = open(fixture, "s02-order-01");
        SamplingVersionView model = fixture.versions.add(
                "s02-ver-0001", keeper, order.samplingCode(), "MODEL", "直径 18 毫米", "",
                "", "", "", "", "", null);
        assertThat(model.versionNo()).isEqualTo(1);
        assertThat(model.decision()).isEqualTo("PENDING");
        assertThat(model.modelReferenced()).isFalse();
        assertThat(model.sourceCustody()).isEqualTo("NONE");
        assertThat(model.backupClaim()).isEqualTo("NONE");
        assertThat(model.parameterText()).isEqualTo("直径 18 毫米");

        SamplingVersionView confirmed = fixture.versions.confirm(
                "s02-yes-0001", keeper, order.samplingCode(), "MODEL", 1, "客户口头确认", "");
        SamplingVersionView replay = fixture.versions.confirm(
                "s02-yes-0001", keeper, order.samplingCode(), "MODEL", 1, "客户口头确认", "");
        assertThat(replay.parameterText()).isEqualTo("直径 18 毫米");
        assertThat(replay.decision()).isEqualTo("CONFIRMED");
        assertThat(replay.decisionLabel()).isEqualTo("已确认");
        assertThat(replay.decidedBy()).isEqualTo("打样员");
        assertThat(fixture.versions.list(keeper, order.samplingCode())).hasSize(1);

        SamplingVersionView next = fixture.versions.add(
                "s02-ver-0002", keeper, order.samplingCode(), "MODEL", "直径 19 毫米", "",
                "戒托.3dm", "3DM", "v3", "样品柜A层", "ATT-SHOT-1", null);
        assertThat(next.versionNo()).isEqualTo(2);
        assertThat(next.modelReferenced()).isTrue();
        assertThat(next.sourceCustody()).isEqualTo("EXTERNAL_REFERENCE");
        assertThat(next.backupClaim()).isEqualTo("NOT_BACKED_UP");
        assertThat(next.modelFileName()).isEqualTo("戒托.3dm");
        assertThat(next.decision()).isEqualTo("PENDING");

        SamplingVersionView original = fixture.versions.list(keeper, order.samplingCode()).get(0);
        assertThat(original.versionNo()).isEqualTo(1);
        assertThat(original.parameterText()).isEqualTo("直径 18 毫米");
        assertThat(original.decision()).isEqualTo("CONFIRMED");
        assertThat(original.modelReferenced()).isFalse();
        assertThatThrownBy(() -> fixture.versions.confirm(
                "s02-yes-0002", keeper, order.samplingCode(), "MODEL", 1, "改口", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该版本已经确认，不能覆盖");
        assertThat(fixture.versions.list(keeper, order.samplingCode()).get(0).parameterText()).isEqualTo("直径 18 毫米");
        assertThat(fixture.orders.find(order.samplingCode()).orElseThrow().status().name()).isEqualTo("OPEN");
        assertThat(fixture.orders.find(order.samplingCode()).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void 驳回和引用边界不改已有版本() {
        Fixture fixture = memory();
        SamplingOrderView order = open(fixture, "s02-order-02");
        fixture.versions.add("s02-flat-001", keeper, order.samplingCode(), "FLAT", "正面图", "ATT-FLAT-1",
                "", "", "", "", "", null);
        assertThatThrownBy(() -> fixture.versions.add(
                "s02-flat-002", keeper, order.samplingCode(), "FLAT", "正面图", "",
                "a.3dm", "3DM", "v1", "", "", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有模型版本可以登记模型引用");
        assertThatThrownBy(() -> fixture.versions.add(
                "s02-model-01", keeper, order.samplingCode(), "MODEL", "", "",
                "a.stl", "STL", "v1", "https://example.com/a.stl", "", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("模型存放说明不能是网址或本机路径");
        assertThatThrownBy(() -> fixture.versions.add(
                "s02-model-02", keeper, order.samplingCode(), "MODEL", "", "",
                "", "", "", "", "", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写尺寸参数或模型引用");
        assertThatThrownBy(() -> fixture.versions.reject(
                "s02-no-00001", keeper, order.samplingCode(), "FLAT", 1, "  ", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写驳回原因");
        SamplingVersionView rejected = fixture.versions.reject(
                "s02-no-00002", keeper, order.samplingCode(), "FLAT", 1, "珠型偏大", "ATT-NO-1");
        assertThat(rejected.decision()).isEqualTo("REJECTED");
        assertThat(rejected.parameterText()).isEqualTo("正面图");
        assertThatThrownBy(() -> fixture.versions.confirm(
                "s02-yes-flat1", keeper, order.samplingCode(), "FLAT", 1, "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该版本已经驳回，请另建版本");
        assertThat(fixture.versions.list(keeper, order.samplingCode())).hasSize(1);
        assertThatThrownBy(() -> fixture.versions.list(keeper, "MD-20261029-0003"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样单不存在");

        assertThat(guard.allowedPermissionsFor(RoleCode.CUSTOMER_PORTAL)).doesNotContain(PermissionCode.SAMPLING_VIEW);
        assertThat(guard.allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN)).doesNotContain(PermissionCode.SAMPLING_MAINTAIN);
        OperatorContext portal = new OperatorContext("客户", RoleCode.CUSTOMER_PORTAL, DataScope.CUSTOMER_RANGE, false, "",
                List.of("1"));
        OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        assertThatThrownBy(() -> fixture.versions.list(portal, order.samplingCode()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.versions.confirm(
                "s02-deny-001", finance, order.samplingCode(), "FLAT", 1, "", ""))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(fixture.versions.list(keeper, order.samplingCode())).singleElement()
                .extracting(SamplingVersionView::decision).isEqualTo("REJECTED");
    }

    @Test
    void 失败不留下版本或确认() {
        Fixture fixture = new Fixture(
                new InMemorySamplingOrderRepository(),
                new InMemorySamplingVersionRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        SamplingOrderView order = open(fixture, "s02-order-03");
        Fixture failing = new Fixture(fixture.orders, fixture.versionRows, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.versions.add(
                "s02-fail-001", keeper, order.samplingCode(), "BEAD", "孔径 2.0", "",
                "", "", "", "", "", null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.versionRows.list(order.samplingCode())).isEmpty();
        assertThat(fixture.orders.find(order.samplingCode())).isPresent();
    }

    @Test
    void jdbc确认往返且失败不覆盖正文() throws Exception {
        JdbcDataSource dataSource = dataSource();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        JdbcSamplingOrderRepository orders = new JdbcSamplingOrderRepository(dataSource);
        JdbcSamplingVersionRepository versions = new JdbcSamplingVersionRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        SamplingOrderService orderService = new SamplingOrderService(customers, orders, unit, guard);
        SamplingVersionService service = new SamplingVersionService(orders, versions, unit, guard);
        SamplingOrderView order = orderService.open(
                "s02-jdbc-ord", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
        SamplingVersionView saved = service.add(
                "s02-jdbc-ver", keeper, order.samplingCode(), "BEAD", "孔径 2.0", "",
                "", "", "", "", "", null);
        SamplingVersionView confirmed = service.confirm(
                "s02-jdbc-yes", keeper, order.samplingCode(), "BEAD", saved.versionNo(), "", "");
        assertThat(confirmed.decision()).isEqualTo("CONFIRMED");
        assertThat(confirmed.backupClaim()).isEqualTo("NONE");
        SamplingVersionService failing = new SamplingVersionService(orders, versions, new JdbcBusinessWorkUnit(
                dataSource, new JdbcCommandRequestRepository(dataSource), (CommandResultProbe) () -> {
            throw new IllegalStateException("失败");
        }), guard);
        assertThatThrownBy(() -> failing.add(
                "s02-jdbc-bad", keeper, order.samplingCode(), "BEAD", "孔径 2.2", "",
                "", "", "", "", "", null)).isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from smp_sampling_version")).isEqualByComparingTo("1");
        assertThat(text(dataSource, "select parameter_text from smp_sampling_version")).isEqualTo("孔径 2.0");
        assertThat(count(dataSource, "select count(*) from smp_sampling_version_decision")).isEqualByComparingTo("1");
        assertThat(orders.find(order.samplingCode()).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void 接口可查询确认后的准确版本() {
        Fixture fixture = memory();
        SamplingOrderView order = open(fixture, "s02-order-04");
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(keeper);
        SamplingVersionController controller = new SamplingVersionController(fixture.versions, actors);
        SamplingVersionView saved = controller.add(order.samplingCode(), new SamplingVersionController.AddRequest(
                "s02-http-ver", "MODEL", "", "", "戒托.3dm", "3DM", "v1", "样品柜", "", null
        ), request).data();
        assertThat(saved.backupClaim()).isEqualTo("NOT_BACKED_UP");
        SamplingVersionView confirmed = controller.confirm(order.samplingCode(), new SamplingVersionController.DecisionRequest(
                "s02-http-yes", "MODEL", 1, "确认", "ATT-YES-1"
        ), request).data();
        assertThat(confirmed.modelLocationNote()).isEqualTo("样品柜");
        assertThat(confirmed.decisionCredential()).isEqualTo("ATT-YES-1");
        assertThat(controller.list(order.samplingCode(), request).data()).singleElement()
                .extracting(SamplingVersionView::parameterText, SamplingVersionView::decision)
                .containsExactly("", "CONFIRMED");
    }

    private Fixture memory() {
        return new Fixture(
                new InMemorySamplingOrderRepository(),
                new InMemorySamplingVersionRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
    }

    private SamplingOrderView open(Fixture fixture, String requestId) {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        SamplingOrderService orders = new SamplingOrderService(customers, fixture.orders, fixture.unit, guard);
        return orders.open(requestId, keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
    }

    private static BigDecimal count(JdbcDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getBigDecimal(1);
        }
    }

    private static String text(JdbcDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        var scripts = MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (int version : new int[]{44, 114, 115}) {
                String sql = scripts.stream().filter(script -> script.version() == version).findFirst().orElseThrow().sql();
                for (String item : MigrationCatalog.statements(sql)) {
                    String text = item.trim();
                    if (text.regionMatches(true, 0, "create", 0, 6)) {
                        statement.execute(text);
                    }
                }
            }
        }
        return dataSource;
    }

    private final class Fixture {
        private final InMemorySamplingOrderRepository orders;
        private final InMemorySamplingVersionRepository versionRows;
        private final com.zhubao.erp.work.app.BusinessWorkUnit unit;
        private final SamplingVersionService versions;

        private Fixture(InMemorySamplingOrderRepository orders, InMemorySamplingVersionRepository versionRows,
                        com.zhubao.erp.work.app.BusinessWorkUnit unit) {
            this.orders = orders;
            this.versionRows = versionRows;
            this.unit = unit;
            this.versions = new SamplingVersionService(orders, versionRows, unit, guard);
        }
    }
}
