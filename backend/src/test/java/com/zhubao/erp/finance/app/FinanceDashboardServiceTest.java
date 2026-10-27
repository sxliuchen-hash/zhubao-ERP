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

        assertThat(company.currentBalances().outstandingAmount()).isEqualByComparingTo("140.00");
        assertThat(company.currentBalances().unpaidAmount()).isEqualByComparingTo("0.00");
        assertThat(company.receivableRanking().total()).isEqualTo(2);
        assertThat(company.receivableRanking().rows()).extracting(row -> row.customerId())
                .containsExactly(1002L, 1001L);
        assertThat(company.trend()).hasSize(6);
        assertThat(company.trend().get(5).yearMonth()).isEqualTo("2026-10");
        assertThat(company.trend().get(0).yearMonth()).isEqualTo("2026-05");
        assertThat(company.operating().revenue()).isEqualByComparingTo("140.00");
        assertThat(company.operating().depreciationCost()).isEqualByComparingTo("0.00");
        assertThat(company.operating().gaps()).contains("折旧未生成，成本按 0");
        assertThat(company.operating().gaps()).noneMatch(gap -> gap.contains("工资未确认"));
        assertThat(company.scope().kind()).isEqualTo("COMPANY");
        assertThat(company.capabilities()).anySatisfy(item -> {
            assertThat(item.part()).isEqualTo("currentBalances");
            assertThat(item.available()).isTrue();
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
        assertThat(stored.currentBalances().outstandingAmount()).isEqualByComparingTo(memory.currentBalances().outstandingAmount());
        assertThat(stored.operating().gaps()).containsExactlyElementsOf(memory.operating().gaps());
        assertThat(jdbc.listReceivables()).hasSize(1);
    }

    @Test
    void currentBalanceIgnoresMonthAndKeepsPrepaymentAndCompanyPayablesSeparate() throws Exception {
        finance.saveReceivable(lineAt("R-OCT", 1001L, "恒信珠宝", "100.00", "2026-10", ""));
        finance.saveReceivable(lineAt("R-RED", 1001L, "恒信珠宝", "-30.00", "2026-11", "R-OCT"));
        finance.registerReceipt(new com.zhubao.erp.finance.domain.CustomerReceipt(
                "RC-1", 1001L, "恒信珠宝", "2026-12", new BigDecimal("50.00"), "2026-12-02",
                List.of(new com.zhubao.erp.finance.domain.ReceiptAllocation("R-OCT", new BigDecimal("20.00"))),
                "财务", "2026-12-02T08:00:00"
        ), "rc-1");
        finance.saveReceivable(lineAt("R-B", 1002L, "瑞禾珠宝", "40.00", "2026-08", ""));
        finance.savePayable(new com.zhubao.erp.finance.domain.Payable(
                "PAY-OUT", "OUTSOURCE", "2026-08", "外协厂", "OS-1", 1002L, "MD-1002",
                new BigDecimal("100.00"), new BigDecimal("40.00"), "财务", "2026-08-01T08:00:00"
        ));
        finance.savePayable(new com.zhubao.erp.finance.domain.Payable(
                "PAY-WAGE", "WAGE", "2026-10", "车间", "PAYROLL-1", null, "",
                new BigDecimal("200.00"), new BigDecimal("0.00"), "财务", "2026-10-01T08:00:00"
        ));
        int before = finance.listReceivables().size();
        OperatorContext companyUser = user(DataScope.ALL, List.of());

        FinanceDashboard september = dashboard.dashboard(companyUser, "2026-09", null);
        FinanceDashboard november = dashboard.dashboard(companyUser, "2026-11", null);
        assertThat(september.currentBalances().outstandingAmount()).isEqualByComparingTo("90.00");
        assertThat(november.currentBalances().outstandingAmount()).isEqualByComparingTo("90.00");
        assertThat(september.currentBalances().unpaidAmount()).isEqualByComparingTo("260.00");
        assertThat(september.currentBalances().asOf()).isNotBlank();
        assertThat(september.operating().revenue()).isEqualByComparingTo("0.00");
        assertThat(finance.listReceivables()).hasSize(before);

        FinanceDashboard customer = dashboard.dashboard(companyUser, "2026-10", 1001L);
        assertThat(customer.currentBalances().outstandingAmount()).isEqualByComparingTo("50.00");
        assertThat(customer.currentBalances().unpaidAmount()).isEqualByComparingTo("0.00");
        assertThat(customer.currentBalances().limitation()).contains("不计入该范围应付");
        assertThat(customer.receivableRanking().rows()).extracting(row -> row.outstandingAmount().toPlainString())
                .containsExactly("50.00");

        var receivable = dashboard.details(companyUser, "RECEIVABLE", null, null, 1, 1);
        assertThat(receivable.total()).isEqualTo(2);
        assertThat(receivable.rows()).hasSize(1);
        assertThat(receivable.totalAmount()).isEqualByComparingTo("90.00");
        var secondPage = dashboard.details(companyUser, "RECEIVABLE", null, null, 2, 1);
        assertThat(secondPage.totalAmount()).isEqualByComparingTo(receivable.totalAmount());
        assertThat(secondPage.rows().get(0).sourceCode()).isNotEqualTo(receivable.rows().get(0).sourceCode());

        var octoberRevenue = dashboard.details(companyUser, "REVENUE", "2026-10", null, 1, 20);
        assertThat(octoberRevenue.totalAmount()).isEqualByComparingTo(
                dashboard.dashboard(companyUser, "2026-10", null).operating().revenue());

        OperatorContext scoped = user(DataScope.CUSTOMER_RANGE, List.of("恒信珠宝"));
        assertThatThrownBy(() -> dashboard.details(scoped, "RECEIVABLE", null, 1002L, 1, 20))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(dashboard.details(scoped, "PAYABLE", null, null, 1, 20).totalAmount()).isEqualByComparingTo("0.00");
        assertThat(dashboard.details(scoped, "PAYABLE", null, null, 1, 20).rows()).isEmpty();

        var jdbc = new com.zhubao.erp.finance.infra.JdbcFinanceRepository(dataSource());
        jdbc.saveReceivable(lineAt("R-OCT", 1001L, "恒信珠宝", "100.00", "2026-10", ""));
        jdbc.saveReceivable(lineAt("R-RED", 1001L, "恒信珠宝", "-30.00", "2026-11", "R-OCT"));
        jdbc.saveReceipt(new com.zhubao.erp.finance.domain.CustomerReceipt(
                "RC-1", 1001L, "恒信珠宝", "2026-12", new BigDecimal("50.00"), "2026-12-02",
                List.of(new com.zhubao.erp.finance.domain.ReceiptAllocation("R-OCT", new BigDecimal("20.00"))),
                "财务", "2026-12-02T08:00:00"
        ));
        jdbc.saveReceivable(lineAt("R-B", 1002L, "瑞禾珠宝", "40.00", "2026-08", ""));
        jdbc.savePayable(new com.zhubao.erp.finance.domain.Payable(
                "PAY-OUT", "OUTSOURCE", "2026-08", "外协厂", "OS-1", 1002L, "MD-1002",
                new BigDecimal("100.00"), new BigDecimal("40.00"), "财务", "2026-08-01T08:00:00"
        ));
        jdbc.savePayable(new com.zhubao.erp.finance.domain.Payable(
                "PAY-WAGE", "WAGE", "2026-10", "车间", "PAYROLL-1", null, "",
                new BigDecimal("200.00"), new BigDecimal("0.00"), "财务", "2026-10-01T08:00:00"
        ));
        FinanceDashboard stored = service(jdbc).dashboard(companyUser, "2026-11", null);
        assertThat(stored.currentBalances().outstandingAmount()).isEqualByComparingTo("90.00");
        assertThat(stored.currentBalances().unpaidAmount()).isEqualByComparingTo("260.00");
        assertThat(service(jdbc).details(companyUser, "RECEIVABLE", null, null, 1, 20).totalAmount())
                .isEqualByComparingTo("90.00");
    }

    private FinanceDashboardService service(com.zhubao.erp.finance.domain.FinanceRepository repository) {
        var billing = new FinanceBillingService(repository, new InMemoryBusinessDocumentRepository(), new StockLedgerService(new InMemoryStockLedgerRepository()));
        return new FinanceDashboardService(
                new FinanceReportService(repository, billing),
                billing,
                new FinanceReadAuthorizer(),
                repository,
                new PermissionGuard()
        );
    }

    private OperatorContext user(DataScope scope, List<String> customers) {
        return new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, scope, true, "", customers);
    }

    private ReceivableLine line(String code, long customerId, String name, String amount) {
        return lineAt(code, customerId, name, amount, "2026-10", code.contains("RED") ? "R1" : "");
    }

    private ReceivableLine lineAt(String code, long customerId, String name, String amount, String yearMonth, String reverses) {
        return new ReceivableLine(
                code,
                customerId,
                name,
                yearMonth,
                reverses.isBlank() ? "MOTHER" : "RED",
                code,
                "MD-" + customerId,
                "",
                "",
                BigDecimal.ONE,
                ChargeUnit.GRAM,
                new BigDecimal(amount),
                new BigDecimal(amount),
                reverses,
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
