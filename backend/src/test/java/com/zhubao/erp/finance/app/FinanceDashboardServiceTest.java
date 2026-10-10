package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceCommands.DepreciationRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveFixedAssetCommand;
import com.zhubao.erp.finance.domain.FinanceDashboard;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceDashboardServiceTest {

    private InMemoryFinanceRepository finance;
    private FinanceDashboardService dashboard;
    private FinanceCostService cost;

    @BeforeEach
    void setUp() {
        finance = new InMemoryFinanceRepository();
        dashboard = service(finance);
        cost = new FinanceCostService(finance);
    }

    @Test
    void aggregatesSixMonthsWithoutWritingOrAllocatingCompanyDepreciation() {
        finance.saveReceivable(line("R1", 1001L, "恒信珠宝", "100.00"));
        finance.saveReceivable(line("R1-RED", 1001L, "恒信珠宝", "-40.00"));
        finance.saveReceivable(line("R2", 1002L, "瑞禾珠宝", "80.00"));
        cost.saveAsset(new SaveFixedAssetCommand("抛光机", "设备", new BigDecimal("1000"), new BigDecimal("0"), "2026-10", 3), "财务");
        int receivables = finance.listReceivables().size();
        int assets = finance.listAssets().size();

        OperatorContext companyUser = user(DataScope.ALL, List.of());
        FinanceDashboard company = dashboard.dashboard(companyUser, "2026-10", null);

        assertThat(company.currentBalances()).isNull();
        assertThat(company.receivableRanking()).isNull();
        assertThat(company.trend()).hasSize(6);
        assertThat(company.trend().get(5).yearMonth()).isEqualTo("2026-10");
        assertThat(company.trend().get(0).yearMonth()).isEqualTo("2026-05");
        assertThat(company.operating().revenue()).isEqualByComparingTo("140.00");
        assertThat(company.operating().depreciationCost()).isEqualByComparingTo("0.00");
        assertThat(company.operating().gaps()).contains("工资未确认，成本按 0", "折旧未生成，成本按 0");
        assertThat(company.scope().kind()).isEqualTo("COMPANY");
        assertThat(company.capabilities()).anySatisfy(item -> {
            assertThat(item.part()).isEqualTo("currentBalances");
            assertThat(item.available()).isFalse();
        });
        assertThat(finance.listReceivables()).hasSize(receivables);
        assertThat(finance.listAssets()).hasSize(assets);
        assertThat(finance.listDepreciations()).isEmpty();

        FinanceDashboard customer = dashboard.dashboard(companyUser, "2026-10", 1001L);
        assertThat(customer.operating().revenue()).isEqualByComparingTo("60.00");
        assertThat(customer.operating().depreciationCost()).isEqualByComparingTo("0.00");
        assertThat(customer.operating().gaps()).doesNotContain("折旧未生成，成本按 0");
        assertThat(customer.scope().customerIds()).containsExactly(1001L);

        OperatorContext scoped = user(DataScope.CUSTOMER_RANGE, List.of("恒信珠宝"));
        FinanceDashboard limited = dashboard.dashboard(scoped, "2026-10", null);
        assertThat(limited.scope().kind()).isEqualTo("CUSTOMERS");
        assertThat(limited.scope().customerIds()).containsExactly(1001L);
        assertThat(limited.operating().revenue()).isEqualByComparingTo("60.00");
        assertThatThrownBy(() -> dashboard.dashboard(scoped, "2026-10", 1002L))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> dashboard.dashboard(companyUser, "2026-13", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM");

        cost.depreciate(new DepreciationRunCommand("2026-10"));
        assertThat(dashboard.dashboard(companyUser, "2026-10", null).operating().depreciationCost())
                .isEqualByComparingTo("333.33");
        assertThat(dashboard.dashboard(companyUser, "2026-10", 1001L).operating().depreciationCost())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void memoryAndJdbcDashboardsUseTheSameReceivableNet() throws Exception {
        ReceivableLine receivable = line("R-JDBC", 1001L, "恒信珠宝", "88.50");
        finance.saveReceivable(receivable);
        var jdbc = new com.zhubao.erp.finance.infra.JdbcFinanceRepository(dataSource());
        jdbc.saveReceivable(receivable);

        OperatorContext companyUser = user(DataScope.ALL, List.of());
        FinanceDashboard memory = dashboard.dashboard(companyUser, "2026-10", 1001L);
        FinanceDashboard stored = service(jdbc).dashboard(companyUser, "2026-10", 1001L);

        assertThat(stored.operating().revenue()).isEqualByComparingTo(memory.operating().revenue());
        assertThat(stored.operating().gaps()).containsExactlyElementsOf(memory.operating().gaps());
        assertThat(jdbc.listReceivables()).hasSize(1);
    }

    private FinanceDashboardService service(com.zhubao.erp.finance.domain.FinanceRepository repository) {
        var billing = new FinanceBillingService(repository, new InMemoryBusinessDocumentRepository(), new StockLedgerService(new InMemoryStockLedgerRepository()));
        return new FinanceDashboardService(
                new FinanceReportService(repository, billing),
                new FinanceReadAuthorizer(),
                repository,
                new PermissionGuard()
        );
    }

    private OperatorContext user(DataScope scope, List<String> customers) {
        return new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, scope, true, "", customers);
    }

    private ReceivableLine line(String code, long customerId, String name, String amount) {
        return new ReceivableLine(
                code,
                customerId,
                name,
                "2026-10",
                code.contains("RED") ? "RED" : "MOTHER",
                code,
                "MD-" + customerId,
                "",
                "",
                BigDecimal.ONE,
                ChargeUnit.GRAM,
                new BigDecimal(amount),
                new BigDecimal(amount),
                code.contains("RED") ? "R1" : "",
                "",
                "财务",
                "2026-10-08T08:00:00"
        );
    }

    private JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        String script;
        try (InputStream input = FinanceDashboardServiceTest.class.getResourceAsStream("/db/mysql/V36__m3_finance_operations.sql")) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String part : script.split(";")) {
                String sql = part.trim();
                if (sql.isEmpty() || sql.contains("sys_navigation_menu") || sql.contains("sys_role_menu")) {
                    continue;
                }
                statement.execute(sql);
            }
        }
        return dataSource;
    }
}
