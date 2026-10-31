package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.BusinessTargetRole;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOrderRepository;
import com.zhubao.erp.sampling.web.SamplingOrderController;
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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SamplingOrderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 建单使用打样号且不依赖母单() {
        InMemoryMasterDataRepository customers = customers();
        CustomerProfile customer = saveCustomer(customers, "甲客户", true);
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        SamplingOrderService service = service(customers, orders, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));

        assertThatThrownBy(() -> service.open(
                "s01-mother1", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "珠型样品", "张三",
                "MD-20261029-0003", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样单不使用普通加工母单");
        assertThatThrownBy(() -> service.open(
                "s01-preset1", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "珠型样品", "张三",
                "", "DY-20261031-0001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样单号由系统分配");
        assertThat(orders.list()).isEmpty();

        SamplingOrderView opened = service.open(
                "s01-open-001", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "18 毫米珠型", "张三", "", "");
        assertThat(opened.samplingCode()).startsWith("DY-" + LocalDate.now(ZONE).toString().replace("-", "") + "-");
        assertThat(opened.businessReference()).isEqualTo("SAMPLING|DOCUMENT|" + opened.samplingCode());
        assertThat(BusinessTargetReference.fromCode(opened.samplingCode()).kind()).isEqualTo(BusinessKind.SAMPLING);
        assertThat(BusinessTargetReference.fromCode(opened.samplingCode()).role()).isEqualTo(BusinessTargetRole.DOCUMENT);
        assertThat(BusinessTargetReference.fromCode("MD-20261029-0003").kind()).isEqualTo(BusinessKind.PROCESSING);
        assertThat(opened.customerId()).isEqualTo(customer.customerId());
        assertThat(opened.customerName()).isEqualTo("甲客户");
        assertThat(opened.materialIntent()).isEqualTo("CUSTOMER_SUPPLIED");
        assertThat(opened.materialIntentLabel()).isEqualTo("客户来料");
        assertThat(opened.requirement()).isEqualTo("18 毫米珠型");
        assertThat(opened.ownerName()).isEqualTo("张三");
        assertThat(opened.status()).isEqualTo("OPEN");
        assertThat(opened.statusLabel()).isEqualTo("进行中");
        assertThat(opened.version()).isEqualTo(1);
        assertThat(opened.openedBy()).isEqualTo("打样员");

        SamplingOrderView replay = service.open(
                "s01-open-001", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "18 毫米珠型", "张三", null, null);
        assertThat(replay.samplingCode()).isEqualTo(opened.samplingCode());
        assertThat(orders.list()).hasSize(1);
        assertThat(service.list(keeper, null)).hasSize(1);
        assertThat(service.list(keeper, customer.customerId())).hasSize(1);

        CustomerProfile other = saveCustomer(customers, "乙客户", true);
        SamplingOrderView company = service.open(
                "s01-open-002", keeper, other.customerId(), "COMPANY_PREPARED", "公司备料打样", "李四", "", "");
        assertThat(company.materialIntentLabel()).isEqualTo("公司自备");
        assertThat(company.samplingCode()).isNotEqualTo(opened.samplingCode());
        assertThat(orders.list()).hasSize(2);
        assertThat(service.list(keeper, customer.customerId())).extracting(SamplingOrderView::samplingCode)
                .containsExactly(opened.samplingCode());
    }

    @Test
    void 缺项和越权不留下打样单() {
        InMemoryMasterDataRepository customers = customers();
        CustomerProfile customer = saveCustomer(customers, "甲客户", true);
        CustomerProfile stopped = saveCustomer(customers, "停用客户", false);
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        SamplingOrderService service = service(customers, orders, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));

        assertThatThrownBy(() -> service.open("s01-miss-001", keeper, null, "CUSTOMER_SUPPLIED", "要求", "张三", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请选择客户");
        assertThatThrownBy(() -> service.open("s01-miss-002", keeper, 99L, "CUSTOMER_SUPPLIED", "要求", "张三", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户档案不存在");
        assertThatThrownBy(() -> service.open("s01-miss-003", keeper, stopped.customerId(), "CUSTOMER_SUPPLIED", "要求", "张三", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户已停用");
        assertThatThrownBy(() -> service.open("s01-miss-004", keeper, customer.customerId(), "", "要求", "张三", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请选择来料或公司自备");
        assertThatThrownBy(() -> service.open("s01-miss-005", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "  ", "张三", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写样品要求");
        assertThatThrownBy(() -> service.open("s01-miss-006", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "要求", "", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写负责人");
        SamplingOrderView opened = service.open(
                "s01-open-010", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "珠型", "张三", "", "");
        assertThatThrownBy(() -> service.open(
                "s01-open-010", keeper, customer.customerId(), "COMPANY_PREPARED", "珠型", "张三", "", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThat(orders.list()).extracting(order -> order.samplingCode()).containsExactly(opened.samplingCode());

        assertThat(guard.allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN)).doesNotContain(PermissionCode.SAMPLING_MAINTAIN);
        assertThat(guard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER)).doesNotContain(PermissionCode.SAMPLING_VIEW);
        assertThat(guard.allowedPermissionsFor(RoleCode.BUSINESS_MANAGER)).doesNotContain(PermissionCode.SAMPLING_MAINTAIN);
        assertThat(guard.allowedPermissionsFor(RoleCode.CUSTOMER_PORTAL)).doesNotContain(PermissionCode.SAMPLING_VIEW);
        OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        OperatorContext portal = new OperatorContext("客户", RoleCode.CUSTOMER_PORTAL, DataScope.CUSTOMER_RANGE, false, "",
                List.of(String.valueOf(customer.customerId())));
        OperatorContext manager = new OperatorContext("业务", RoleCode.BUSINESS_MANAGER, DataScope.ALL, false, "",
                List.of(), List.of("报工"));
        assertThatThrownBy(() -> service.open("s01-deny-001", finance, customer.customerId(), "CUSTOMER_SUPPLIED", "要求", "张三", "", ""))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.list(portal, null)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.open("s01-deny-002", manager, customer.customerId(), "CUSTOMER_SUPPLIED", "要求", "张三", "", ""))
                .isInstanceOf(PermissionDeniedException.class);
        OperatorContext scoped = new OperatorContext("范围", RoleCode.ADMIN, DataScope.CUSTOMER_RANGE, false, "", List.of());
        assertThatThrownBy(() -> service.list(scoped, null))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("客户范围账号没有绑定客户");
        OperatorContext otherScope = new OperatorContext("范围", RoleCode.ADMIN, DataScope.CUSTOMER_RANGE, false, "",
                List.of("客户#999"));
        assertThatThrownBy(() -> service.open(
                "s01-deny-003", otherScope, customer.customerId(), "CUSTOMER_SUPPLIED", "要求", "张三", "", ""))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("客户不在当前账号范围内");
        assertThat(orders.list()).hasSize(1);
    }

    @Test
    void 失败不留下打样单() {
        InMemoryMasterDataRepository customers = customers();
        CustomerProfile customer = saveCustomer(customers, "甲客户", true);
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        SamplingOrderService failing = service(customers, orders, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.open(
                "s01-fail-001", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "要求", "张三", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(orders.list()).isEmpty();
    }

    @Test
    void jdbc建单可回放且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        InMemoryMasterDataRepository customers = customers();
        CustomerProfile customer = saveCustomer(customers, "甲客户", true);
        JdbcSamplingOrderRepository orders = new JdbcSamplingOrderRepository(dataSource);
        SamplingOrderService service = new SamplingOrderService(
                customers, orders, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        SamplingOrderView opened = service.open(
                "s01-jdbc-001", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "18 毫米", "张三", "", "");
        SamplingOrderView replay = service.open(
                "s01-jdbc-001", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "18 毫米", "张三", "", "");
        assertThat(replay.samplingCode()).isEqualTo(opened.samplingCode());
        assertThat(replay.businessReference()).startsWith("SAMPLING|DOCUMENT|DY-");
        assertThat(orders.list()).hasSize(1);
        assertThat(count(dataSource, "select count(*) from smp_sampling_order")).isEqualByComparingTo("1");

        SamplingOrderService failing = new SamplingOrderService(
                customers, orders, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), (CommandResultProbe) () -> {
            throw new IllegalStateException("失败");
        }), guard);
        assertThatThrownBy(() -> failing.open(
                "s01-jdbc-002", keeper, customer.customerId(), "COMPANY_PREPARED", "备料", "李四", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "select count(*) from smp_sampling_order")).isEqualByComparingTo("1");
        assertThat(count(dataSource, "select count(*) from biz_command_request where request_id='s01-jdbc-002'"))
                .isEqualByComparingTo("0");
    }

    @Test
    void 接口建单不接收母单号() {
        InMemoryMasterDataRepository customers = customers();
        CustomerProfile customer = saveCustomer(customers, "甲客户", true);
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        SamplingOrderService service = service(customers, orders, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(keeper);
        SamplingOrderController controller = new SamplingOrderController(service, actors);
        SamplingOrderView saved = controller.open(new SamplingOrderController.OpenRequest(
                "s01-http-001", customer.customerId(), "COMPANY_PREPARED", "公司备料", "李四", "", ""
        ), request).data();
        assertThat(saved.statusLabel()).isEqualTo("进行中");
        assertThat(saved.materialIntentLabel()).isEqualTo("公司自备");
        assertThat(controller.list(null, request).data()).hasSize(1);
        assertThat(orders.list()).hasSize(1);
    }

    private SamplingOrderService service(InMemoryMasterDataRepository customers, InMemorySamplingOrderRepository orders,
                                         com.zhubao.erp.work.app.BusinessWorkUnit unit) {
        return new SamplingOrderService(customers, orders, unit, guard);
    }

    private static InMemoryMasterDataRepository customers() {
        return new InMemoryMasterDataRepository();
    }

    private static CustomerProfile saveCustomer(InMemoryMasterDataRepository customers, String name, boolean enabled) {
        return customers.saveCustomer(new CustomerProfile(0, "", name, "", "", "", "", enabled));
    }

    private static java.math.BigDecimal count(JdbcDataSource dataSource, String sql) throws Exception {
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
            for (int version : new int[]{44, 114}) {
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
}
