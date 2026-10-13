package com.zhubao.erp.finance.infra;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.ConsumableItem;
import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.PriceLine;
import com.zhubao.erp.finance.domain.PricingMode;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.WorkerRate;
import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFinanceRepositoryTest {

    private JdbcFinanceRepository repository;
    private DataSource dataSource;
    private MysqlDataSource mysqlServerDataSource;
    private String mysqlDatabase;

    @BeforeEach
    void setUp() throws Exception {
        String mysqlUrl = System.getProperty("zhubao.test.mysql.url", "");
        if (mysqlUrl.isBlank()) {
            JdbcDataSource h2DataSource = new JdbcDataSource();
            h2DataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
            h2DataSource.setUser("sa");
            h2DataSource.setPassword("");
            dataSource = h2DataSource;
        } else {
            dataSource = isolatedMysqlDataSource(mysqlUrl);
        }
        List<MigrationScript> migrations = MigrationCatalog.loadClasspath();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (int index : new int[]{35, 39, 40}) {
                for (String sql : MigrationCatalog.statements(migrations.get(index).sql())) {
                    if (sql.contains("sys_navigation_menu") || sql.contains("sys_role_menu")) {
                        continue;
                    }
                    statement.execute(sql);
                }
            }
        }
        repository = new JdbcFinanceRepository(dataSource);
    }

    private DataSource isolatedMysqlDataSource(String url) throws Exception {
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("MySQL 测试地址必须以 jdbc:mysql:// 开头");
        }
        URI uri = URI.create(url.substring("jdbc:".length()));
        if (uri.getRawAuthority() == null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("MySQL 测试地址缺少服务器或包含无效片段");
        }
        // 丢弃传入地址中的库名，管理连接和仓储连接均不进入外部业务库。
        String serverUrl = "jdbc:mysql://" + uri.getRawAuthority() + "/";
        String options = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        mysqlServerDataSource = mysqlDataSource(serverUrl + options);
        String database = "zhubao_finance_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = mysqlServerDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(8);
            assertThat(connection.getMetaData().getDatabaseMinorVersion()).isEqualTo(4);
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            mysqlDatabase = database;
        }
        return mysqlDataSource(serverUrl + database + options);
    }

    private MysqlDataSource mysqlDataSource(String url) {
        MysqlDataSource mysqlDataSource = new MysqlDataSource();
        mysqlDataSource.setURL(url);
        mysqlDataSource.setUser(System.getProperty("zhubao.test.mysql.user", "root"));
        mysqlDataSource.setPassword(System.getProperty("zhubao.test.mysql.password", ""));
        return mysqlDataSource;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mysqlDatabase != null) {
            // 仅删除本次测试成功创建并持有名称的 UUID 隔离库。
            try (Connection connection = mysqlServerDataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + mysqlDatabase + "`");
            }
        } else if (dataSource instanceof JdbcDataSource) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void 协议应收收款工资和应付可以往返() {
        repository.saveAgreement(new PriceAgreement(
                "FIN-AGR-1",
                1001L,
                "恒信珠宝",
                PricingMode.PROCESS,
                "2026-10",
                "",
                List.of(new PriceLine("雕刻", ChargeUnit.JOB, new BigDecimal("30.0000"))),
                "财务",
                "2026-10-09T10:00:00"
        ));
        repository.saveReceivable(new ReceivableLine(
                "FIN-AR-1",
                1001L,
                "恒信珠宝",
                "2026-10",
                "PROCESS",
                "PROCESS|ZD-1|雕刻",
                "MD-1",
                "ZD-1",
                "雕刻",
                new BigDecimal("1.000"),
                ChargeUnit.JOB,
                new BigDecimal("30.0000"),
                new BigDecimal("30.00"),
                "",
                "",
                "财务入账",
                "2026-10-09T10:01:00"
        ));
        repository.saveReceipt(new CustomerReceipt(
                "FIN-RC-1",
                1001L,
                "恒信珠宝",
                "2026-10",
                new BigDecimal("30.00"),
                "2026-10-09",
                List.of(new ReceiptAllocation("FIN-AR-1", new BigDecimal("30.00"))),
                "财务",
                "2026-10-09T10:02:00"
        ));
        repository.saveWorkerRate(new WorkerRate(
                "FIN-WR-1", "雕刻", ChargeUnit.JOB, new BigDecimal("12.0000"), "2026-10", "", "财务", "2026-10-09T10:03:00"
        ));
        repository.replacePayroll("2026-10", List.of(new PayrollLine(
                "FIN-PAY-1",
                "2026-10",
                "王强",
                "ZD-1",
                "雕刻",
                "MD-1",
                1001L,
                new BigDecimal("1.000"),
                ChargeUnit.JOB,
                new BigDecimal("12.0000"),
                new BigDecimal("12.00"),
                new BigDecimal("0.00"),
                new BigDecimal("12.00"),
                false,
                "PAY|ZD-1|雕刻"
        )));
        assertThat(repository.listPayrollLines()).singleElement().satisfies(line -> {
            assertThat(line.code()).isEqualTo("FIN-PAY-1");
            assertThat(line.yearMonth()).isEqualTo("2026-10");
            assertThat(line.confirmed()).isFalse();
        });
        repository.replacePayroll("2026-10", List.of(new PayrollLine(
                "FIN-PAY-2",
                "2026-10",
                "王强",
                "ZD-1",
                "雕刻",
                "MD-1",
                1001L,
                new BigDecimal("2.000"),
                ChargeUnit.JOB,
                new BigDecimal("12.0000"),
                new BigDecimal("24.00"),
                new BigDecimal("0.00"),
                new BigDecimal("24.00"),
                true,
                "PAY|ZD-1|雕刻"
        )));
        repository.savePayrollRun(new PayrollRun("2026-10", false, "", ""));
        assertThat(repository.findPayrollRun("2026-10")).get().satisfies(run -> {
            assertThat(run.yearMonth()).isEqualTo("2026-10");
            assertThat(run.confirmed()).isFalse();
        });
        repository.savePayrollRun(new PayrollRun("2026-10", true, "财务", "2026-10-09T10:04:00"));
        repository.saveConsumableItem(new ConsumableItem("GLUE", "胶", "支"));
        repository.saveConsumableMovement(new ConsumableMovement(
                "FIN-PUR-1", "GLUE", "PURCHASE", new BigDecimal("2.000"), new BigDecimal("8.00"),
                "胶行", null, "2026-10", "FIN-AP-1", "财务", "2026-10-09T10:05:00"
        ));
        repository.saveAsset(new FixedAsset(
                "FIN-AST-1", "抛光机", "设备", new BigDecimal("1000.00"), new BigDecimal("100.00"),
                "2026-10", 36, "财务", "2026-10-09T10:06:00"
        ));
        repository.saveDepreciation(new DepreciationEntry("FIN-DEP-1", "FIN-AST-1", "2026-10", new BigDecimal("25.00")));
        repository.savePayable(new Payable(
                "FIN-AP-1", "CONSUMABLE", "2026-10", "胶行", "CONSUMABLE|FIN-AP-1", null, "",
                new BigDecimal("8.00"), new BigDecimal("0.00"), "财务", "2026-10-09T10:05:00"
        ));
        assertThat(repository.findPayableBySource("CONSUMABLE|FIN-AP-1")).get().satisfies(payable -> {
            assertThat(payable.yearMonth()).isEqualTo("2026-10");
            assertThat(payable.paidAmount()).isEqualByComparingTo("0.00");
        });
        repository.savePayable(new Payable(
                "FIN-AP-1", "CONSUMABLE", "2026-11", "胶行", "CONSUMABLE|FIN-AP-1", null, "",
                new BigDecimal("8.00"), new BigDecimal("8.00"), "财务", "2026-10-09T10:07:00"
        ));
        repository.savePayablePayment(new PayablePayment(
                "FIN-PMT-1", "FIN-AP-1", new BigDecimal("8.00"), "2026-10-09", "财务", "2026-10-09T10:07:00"
        ));

        assertThat(repository.listAgreements()).singleElement().satisfies(agreement -> {
            assertThat(agreement.mode()).isEqualTo(PricingMode.PROCESS);
            assertThat(agreement.lines()).singleElement().satisfies(line -> assertThat(line.processName()).isEqualTo("雕刻"));
        });
        assertThat(repository.findReceivableBySourceKey("PROCESS|ZD-1|雕刻")).isPresent();
        assertThat(repository.listReceivables()).singleElement()
                .satisfies(line -> assertThat(line.yearMonth()).isEqualTo("2026-10"));
        assertThat(repository.listReceipts()).singleElement()
                .satisfies(receipt -> assertThat(receipt.yearMonth()).isEqualTo("2026-10"));
        assertThat(repository.listReceipts().get(0).allocations()).singleElement()
                .satisfies(allocation -> assertThat(allocation.amount()).isEqualByComparingTo("30.00"));
        assertThat(repository.findPayrollRun("2026-10")).get().satisfies(run -> {
            assertThat(run.yearMonth()).isEqualTo("2026-10");
            assertThat(run.confirmed()).isTrue();
            assertThat(run.confirmedBy()).isEqualTo("财务");
            assertThat(run.confirmedAt()).isEqualTo("2026-10-09T10:04:00");
        });
        assertThat(repository.listPayrollLines()).singleElement().satisfies(line -> {
            assertThat(line.code()).isEqualTo("FIN-PAY-2");
            assertThat(line.yearMonth()).isEqualTo("2026-10");
            assertThat(line.netAmount()).isEqualByComparingTo("24.00");
            assertThat(line.confirmed()).isTrue();
        });
        assertThat(repository.findConsumableItem("GLUE")).isPresent();
        assertThat(repository.listConsumableMovements()).singleElement()
                .satisfies(movement -> assertThat(movement.yearMonth()).isEqualTo("2026-10"));
        assertThat(repository.listDepreciations()).singleElement().satisfies(entry -> {
            assertThat(entry.yearMonth()).isEqualTo("2026-10");
            assertThat(entry.amount()).isEqualByComparingTo("25.00");
        });
        assertThat(repository.listPayables()).singleElement().satisfies(payable -> {
            assertThat(payable.code()).isEqualTo("FIN-AP-1");
            assertThat(payable.yearMonth()).isEqualTo("2026-11");
            assertThat(payable.paidAmount()).isEqualByComparingTo("8.00");
        });
        assertThat(repository.findPayableBySource("CONSUMABLE|FIN-AP-1")).get()
                .satisfies(payable -> assertThat(payable.yearMonth()).isEqualTo("2026-11"));
        assertThat(repository.listPayablePayments()).hasSize(1);
    }
}
