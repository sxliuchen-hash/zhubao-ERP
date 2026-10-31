package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.sampling.domain.SamplingDelivery;
import com.zhubao.erp.sampling.domain.SamplingFeeView;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.infra.InMemorySamplingDeliveryRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingFeeRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingDeliveryRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingFeeRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOrderRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SamplingFeeSettlementTest {

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 按次确认样费且免收不写零元应收() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        InMemorySamplingDeliveryRepository deliveries = new InMemorySamplingDeliveryRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        SamplingOrderView order = open(orders, unit, "s06-order-01");
        seed(unit, deliveries, order.samplingCode(), "SD-S06-A");
        seed(unit, deliveries, order.samplingCode(), "SD-S06-B");
        SamplingFeeService service = new SamplingFeeService(
                orders, deliveries, new InMemorySamplingFeeRepository(), finance, unit, guard);

        assertThat(guard.allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN)).doesNotContain(PermissionCode.SAMPLING_FEE_WAIVE);
        OperatorContext financeUser = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        assertThatThrownBy(() -> service.settle(
                "s06-deny-001", financeUser, order.samplingCode(), "SD-S06-A", new BigDecimal("80.00"), false, "", "", false))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(finance.listReceivables()).isEmpty();

        assertThatThrownBy(() -> service.settle(
                "s06-zero-001", keeper, order.samplingCode(), "SD-S06-A", BigDecimal.ZERO, false, "", "", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知金额不能写成 0");
        assertThatThrownBy(() -> service.settle(
                "s06-off-0001", keeper, order.samplingCode(), "SD-S06-A", new BigDecimal("80.00"), false, "", "MD-20261029-0003", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("样费不能抵大货");
        assertThatThrownBy(() -> service.settle(
                "s06-pay-0001", keeper, order.samplingCode(), "SD-S06-A", new BigDecimal("80.00"), false, "", "", true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样员工计酬尚未核定");
        assertThat(finance.listReceivables()).isEmpty();
        assertThat(finance.listPayrollLines()).isEmpty();

        SamplingFeeView charged = service.settle(
                "s06-fee-0001", keeper, order.samplingCode(), "SD-S06-A", new BigDecimal("80.00"), false, "", "", false);
        SamplingFeeView replay = service.settle(
                "s06-fee-0001", keeper, order.samplingCode(), "SD-S06-A", new BigDecimal("80.00"), false, "", "", false);
        assertThat(replay.feeId()).isEqualTo(charged.feeId());
        assertThat(charged.amount()).isEqualByComparingTo("80.00");
        assertThat(charged.motherOrderCode()).isEmpty();
        assertThat(charged.wagePosted()).isFalse();
        assertThat(finance.listReceivables()).hasSize(1);
        ReceivableLine line = finance.listReceivables().get(0);
        assertThat(line.amount()).isEqualByComparingTo("80.00");
        assertThat(line.sourceType()).isEqualTo("SAMPLING_FEE");
        assertThat(line.sourceKey()).isEqualTo("SD-S06-A");
        assertThat(line.motherOrderCode()).isEmpty();
        assertThat(line.workOrderCode()).isEqualTo(order.samplingCode());
        assertThat(finance.listPayrollLines()).isEmpty();
        assertThatThrownBy(() -> service.settle(
                "s06-fee-0002", keeper, order.samplingCode(), "SD-S06-A", new BigDecimal("90.00"), false, "", "", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这次交付的样费已经确认");
        assertThat(finance.listReceivables()).hasSize(1);

        SamplingFeeView waived = service.settle(
                "s06-free001", keeper, order.samplingCode(), "SD-S06-B", null, true, "客户已付过大货定金外的样品", "", false);
        assertThat(waived.decision()).isEqualTo("WAIVE");
        assertThat(waived.amount()).isNull();
        assertThat(waived.receivableCode()).isEmpty();
        assertThat(waived.reason()).isEqualTo("客户已付过大货定金外的样品");
        assertThat(finance.listReceivables()).hasSize(1);
        assertThat(finance.listPayrollLines()).isEmpty();
    }

    @Test
    void jdbc样费失败不留下应收() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcSamplingOrderRepository orders = new JdbcSamplingOrderRepository(dataSource);
        JdbcSamplingDeliveryRepository deliveries = new JdbcSamplingDeliveryRepository(dataSource);
        JdbcFinanceRepository finance = new JdbcFinanceRepository(dataSource);
        SamplingOrderView order = open(orders, unit, "s06-jdbc-od");
        seed(unit, deliveries, order.samplingCode(), "SD-JDBC-1");
        SamplingFeeService service = new SamplingFeeService(
                orders, deliveries, new JdbcSamplingFeeRepository(dataSource), finance, unit, guard);
        SamplingFeeView charged = service.settle(
                "s06-jdbc-f1", keeper, order.samplingCode(), "SD-JDBC-1", new BigDecimal("15.50"), false, "", "", false);
        assertThat(charged.amount()).isEqualByComparingTo("15.50");
        assertThat(finance.listReceivables()).singleElement().extracting(ReceivableLine::motherOrderCode).isEqualTo("");
        assertThat(finance.listPayrollLines()).isEmpty();
        seed(unit, deliveries, order.samplingCode(), "SD-JDBC-2");
        SamplingFeeService failing = new SamplingFeeService(
                orders, deliveries, new JdbcSamplingFeeRepository(dataSource), finance,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.settle(
                "s06-jdbc-x1", keeper, order.samplingCode(), "SD-JDBC-2", new BigDecimal("9.00"), false, "", "", false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(finance.listReceivables()).hasSize(1);
    }

    private SamplingOrderView open(com.zhubao.erp.sampling.domain.SamplingOrderRepository orders, BusinessWorkUnit unit, String requestId) {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        return new SamplingOrderService(customers, orders, unit, guard).open(
                requestId, keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
    }

    private void seed(BusinessWorkUnit unit, com.zhubao.erp.sampling.domain.SamplingDeliveryRepository deliveries,
                      String samplingCode, String deliveryId) {
        unit.execute(new CommandCall("seed-" + deliveryId, keeper.operator(), "SEED_DELIVERY", "SAMPLING_DELIVERY",
                samplingCode, deliveryId), session -> {
            deliveries.insert(new SamplingDelivery(
                    deliveryId, samplingCode, SamplingVersionKind.MODEL, 1, "", new BigDecimal("1"), "颗", "ATT-1", "2026-10-31"
            ), session);
            return deliveryId;
        });
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V36__m3_finance_operations.sql", "V44__m3_command_request.sql",
                    "V114__m3_sampling_order.sql", "V118__m3_sampling_delivery.sql", "V119__m3_sampling_fee.sql"}) {
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
