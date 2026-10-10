package com.zhubao.erp.finance.infra;

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

class JdbcFinanceRepositoryTest {

    private JdbcFinanceRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        String script;
        try (InputStream input = JdbcFinanceRepositoryTest.class.getResourceAsStream("/db/mysql/V36__m3_finance_operations.sql")) {
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
        repository = new JdbcFinanceRepository(dataSource);
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
                true,
                "PAY|ZD-1|雕刻"
        )));
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
        repository.savePayablePayment(new PayablePayment(
                "FIN-PMT-1", "FIN-AP-1", new BigDecimal("8.00"), "2026-10-09", "财务", "2026-10-09T10:07:00"
        ));

        assertThat(repository.listAgreements()).singleElement().satisfies(agreement -> {
            assertThat(agreement.mode()).isEqualTo(PricingMode.PROCESS);
            assertThat(agreement.lines()).singleElement().satisfies(line -> assertThat(line.processName()).isEqualTo("雕刻"));
        });
        assertThat(repository.findReceivableBySourceKey("PROCESS|ZD-1|雕刻")).isPresent();
        assertThat(repository.listReceipts().get(0).allocations()).singleElement()
                .satisfies(allocation -> assertThat(allocation.amount()).isEqualByComparingTo("30.00"));
        assertThat(repository.findPayrollRun("2026-10")).get().satisfies(run -> assertThat(run.confirmed()).isTrue());
        assertThat(repository.listPayrollLines()).singleElement().satisfies(line -> assertThat(line.confirmed()).isTrue());
        assertThat(repository.findConsumableItem("GLUE")).isPresent();
        assertThat(repository.listDepreciations()).singleElement()
                .satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("25.00"));
        assertThat(repository.findPayableBySource("CONSUMABLE|FIN-AP-1")).isPresent();
        assertThat(repository.listPayablePayments()).hasSize(1);
    }
}
