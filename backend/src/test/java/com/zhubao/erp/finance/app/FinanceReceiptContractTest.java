package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.FinanceCommands.ReceiptAllocationCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.RecordReceiptCommand;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.FinanceReceiptProbe;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内存与 H2 MySQL 兼容模式共用的收款幂等行为。H2 不是真实 MySQL。
 * 客户范围授权仍由收款接口在调用本服务之前检查，本测试不放宽该规则。
 */
class FinanceReceiptContractTest {

    @Test
    void memoryAndJdbcReturnTheOriginalReceiptForTheSameKey() throws Exception {
        assertBehavior(new InMemoryFinanceRepository());
        assertBehavior(jdbcRepository(new AtomicReference<>("none")));
    }

    @Test
    void allocatedReceiptReplaysBeforeCheckingTheReducedBalance() {
        InMemoryFinanceRepository repository = new InMemoryFinanceRepository();
        repository.saveReceivable(receivable("AR-FULL", "80.00"));
        repository.saveReceivable(receivable("AR-PART", "80.00"));
        FinanceBillingService service = service(repository);
        RecordReceiptCommand full = allocated("80.00", "rc-full-80", "AR-FULL", "80.00");
        CustomerReceipt first = service.receive(full, "财务");
        CustomerReceipt replay = service.receive(full, "财务");
        assertThat(replay.code()).isEqualTo(first.code());
        assertThat(repository.listReceipts()).hasSize(1);

        RecordReceiptCommand partial = allocated("30.00", "rc-part-30", "AR-PART", "30.00");
        CustomerReceipt part = service.receive(partial, "财务");
        service.receive(allocated("50.00", "rc-part-next", "AR-PART", "50.00"), "财务");
        CustomerReceipt partReplay = service.receive(partial, "财务");
        assertThat(partReplay.code()).isEqualTo(part.code());
        assertThat(partReplay.allocations()).singleElement().extracting(item -> item.amount().toPlainString()).isEqualTo("30.00");
        assertThat(repository.listReceipts()).hasSize(3);

        assertThatThrownBy(() -> service.receive(allocated("70.00", "rc-full-80", "AR-FULL", "70.00"), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(repository.listReceipts()).hasSize(3);
        assertThat(service.statement(1008L, "").outstandingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void allocationFailureRollsBackAndRetrySucceeds() throws Exception {
        assertRollback(memoryWithProbe());
        assertRollback(jdbcWithProbe());
    }

    private void assertBehavior(FinanceRepository repository) throws Exception {
        FinanceBillingService service = service(repository);
        CustomerReceipt first = service.receive(command("80.00", "rc-1"), "财务");
        CustomerReceipt replay = service.receive(command("80.00", "rc-1"), "财务");
        assertThat(replay.code()).isEqualTo(first.code());
        assertThat(repository.listReceipts()).hasSize(1);

        assertThatThrownBy(() -> service.receive(command("70.00", "rc-1"), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(repository.listReceipts()).hasSize(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<String> left = new AtomicReference<>();
        AtomicReference<String> right = new AtomicReference<>();
        try {
            Future<?> firstCall = pool.submit(() -> left.set(service.receive(command("80.00", "rc-same"), "财务").code()));
            Future<?> secondCall = pool.submit(() -> right.set(service.receive(command("80.00", "rc-same"), "财务").code()));
            firstCall.get(8, TimeUnit.SECONDS);
            secondCall.get(8, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(left.get()).isEqualTo(right.get());
        assertThat(repository.listReceipts()).hasSize(2);
    }

    private void assertRollback(ProbeHarness harness) {
        assertThatThrownBy(() -> harness.service().receive(command("80.00", "rc-fail"), "财务"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(harness.repository().listReceipts()).isEmpty();
        harness.failure().set("none");
        harness.service().receive(command("80.00", "rc-fail"), "财务");
        assertThat(harness.repository().listReceipts()).hasSize(1);
    }

    private ProbeHarness memoryWithProbe() {
        AtomicReference<String> failure = new AtomicReference<>("allocation");
        InMemoryFinanceRepository repository = new InMemoryFinanceRepository(null, probe(failure));
        return new ProbeHarness(repository, service(repository), failure);
    }

    private ProbeHarness jdbcWithProbe() throws Exception {
        AtomicReference<String> failure = new AtomicReference<>("allocation");
        JdbcFinanceRepository repository = jdbcRepository(failure);
        return new ProbeHarness(repository, service(repository), failure);
    }

    private JdbcFinanceRepository jdbcRepository(AtomicReference<String> failure) throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:finance-rc-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        return new JdbcFinanceRepository(dataSource, null, probe(failure));
    }

    private FinanceBillingService service(FinanceRepository repository) {
        return new FinanceBillingService(repository, null, new StockLedgerService(new InMemoryStockLedgerRepository()));
    }

    private FinanceReceiptProbe probe(AtomicReference<String> failure) {
        return () -> {
            if ("allocation".equals(failure.get())) {
                throw new IllegalStateException("allocation save failed");
            }
        };
    }

    private ReceivableLine receivable(String code, String amount) {
        return new ReceivableLine(code, 1008L, "恒信珠宝", "2026-10", "FIXTURE", "FIX-" + code, "MD-REPLAY", code,
                "", BigDecimal.ONE, ChargeUnit.PIECE, BigDecimal.ONE, new BigDecimal(amount), "", "历史夹具", "财务",
                "2026-10-01T00:00:00");
    }

    private RecordReceiptCommand allocated(String amount, String key, String receivableCode, String allocation) {
        return new RecordReceiptCommand(1008L, "恒信珠宝", "2026-10-10", new BigDecimal(amount),
                List.of(new ReceiptAllocationCommand(receivableCode, new BigDecimal(allocation))), key);
    }

    private RecordReceiptCommand command(String amount, String key) {
        return new RecordReceiptCommand(1008L, "恒信珠宝", "2026-10-10", new BigDecimal(amount), List.of(), key);
    }

    private void createSchema(JdbcDataSource dataSource) throws Exception {
        String receipt = """
                create table fin_customer_receipt (
                    code varchar(80) primary key,
                    customer_id bigint not null,
                    customer_name varchar(120) not null,
                    year_month char(7) not null,
                    amount decimal(18, 2) not null,
                    received_on char(10) not null,
                    operator_name varchar(80) not null,
                    created_at varchar(40) not null
                )
                """;
        String allocation = """
                create table fin_receipt_allocation (
                    receipt_code varchar(80) not null,
                    line_no int not null,
                    receivable_code varchar(80) not null,
                    amount decimal(18, 2) not null,
                    primary key (receipt_code, line_no)
                )
                """;
        String migration;
        try (InputStream input = FinanceReceiptContractTest.class.getResourceAsStream(
                "/db/mysql/V41__m3_finance_receipt_idempotency.sql")) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(receipt);
            statement.execute(allocation);
            for (String part : migration.split(";")) {
                String sql = part.lines()
                        .filter(line -> !line.trim().startsWith("--"))
                        .reduce("", (left, right) -> left + "\n" + right)
                        .trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private record ProbeHarness(FinanceRepository repository, FinanceBillingService service, AtomicReference<String> failure) {
    }
}
