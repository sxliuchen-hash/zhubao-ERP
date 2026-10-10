package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FinanceCommands.PayablePaymentCommand;
import com.zhubao.erp.finance.domain.FinancePaymentProbe;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内存与 H2 MySQL 兼容模式共用的付款行为。H2 不是真实 MySQL。
 * 修复前应付 100、并发各付 60：两次都成功，流水合计 120.00，已付余额仍是 60.00。
 * 余额保存失败时已留下 1 笔 60.00 流水，已付余额仍是 0.00。
 */
class FinancePaymentContractTest {

    @Test
    void memoryAndJdbcKeepPaymentTotalEqualToPaidAmount() throws Exception {
        assertBehavior(memory());
        assertBehavior(jdbc());
    }

    private void assertBehavior(Harness harness) throws Exception {
        String replayCode = "FIN-AP-REPLAY";
        harness.save(payable(replayCode, "100.00", "0.00"));
        PayablePayment first = harness.pay(replayCode, "60.00", "pay-60");
        PayablePayment replay = harness.pay(replayCode, "60.00", "pay-60");
        assertThat(replay.code()).isEqualTo(first.code());
        assertThat(harness.paymentCount(replayCode)).isEqualTo(1);
        assertThat(harness.paid(replayCode)).isEqualByComparingTo("60.00");
        assertThat(harness.paymentTotal(replayCode)).isEqualByComparingTo(harness.paid(replayCode));

        assertThatThrownBy(() -> harness.pay(replayCode, "40.00", "pay-60"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(harness.paymentCount(replayCode)).isEqualTo(1);
        assertThat(harness.paid(replayCode)).isEqualByComparingTo("60.00");

        assertThatThrownBy(() -> harness.pay(replayCode, "60.00", "pay-other"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过");
        assertThat(harness.paymentCount(replayCode)).isEqualTo(1);

        String raceCode = "FIN-AP-RACE";
        harness.save(payable(raceCode, "100.00", "0.00"));
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> left = pool.submit(() -> run(harness, raceCode, "60.00", "race-a", success, rejected));
            Future<?> right = pool.submit(() -> run(harness, raceCode, "60.00", "race-b", success, rejected));
            left.get(8, TimeUnit.SECONDS);
            right.get(8, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(harness.paymentCount(raceCode)).isEqualTo(1);
        assertThat(harness.paid(raceCode)).isEqualByComparingTo("60.00");
        assertThat(harness.paymentTotal(raceCode)).isEqualByComparingTo("60.00");

        String sameCode = "FIN-AP-SAME";
        harness.save(payable(sameCode, "100.00", "0.00"));
        ExecutorService sameKey = Executors.newFixedThreadPool(2);
        AtomicReference<String> leftCode = new AtomicReference<>();
        AtomicReference<String> rightCode = new AtomicReference<>();
        try {
            Future<?> left = sameKey.submit(() -> leftCode.set(harness.pay(sameCode, "60.00", "same-key").code()));
            Future<?> right = sameKey.submit(() -> rightCode.set(harness.pay(sameCode, "60.00", "same-key").code()));
            left.get(8, TimeUnit.SECONDS);
            right.get(8, TimeUnit.SECONDS);
        } finally {
            sameKey.shutdownNow();
        }
        assertThat(leftCode.get()).isEqualTo(rightCode.get());
        assertThat(harness.paymentCount(sameCode)).isEqualTo(1);
        assertThat(harness.paid(sameCode)).isEqualByComparingTo("60.00");
        assertThat(harness.paymentTotal(sameCode)).isEqualByComparingTo("60.00");

        String balanceCode = "FIN-AP-BALANCE";
        harness.save(payable(balanceCode, "100.00", "0.00"));
        harness.failAt("balance");
        assertThatThrownBy(() -> harness.pay(balanceCode, "60.00", "balance-fail"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(harness.paymentCount(balanceCode)).isZero();
        assertThat(harness.paid(balanceCode)).isEqualByComparingTo("0.00");
        harness.failAt("none");
        harness.pay(balanceCode, "60.00", "balance-fail");
        assertThat(harness.paymentCount(balanceCode)).isEqualTo(1);
        assertThat(harness.paid(balanceCode)).isEqualByComparingTo("60.00");
        assertThat(harness.paymentTotal(balanceCode)).isEqualByComparingTo("60.00");
    }

    private void run(Harness harness, String payableCode, String amount, String key, AtomicInteger success, AtomicInteger rejected) {
        try {
            harness.pay(payableCode, amount, key);
            success.incrementAndGet();
        } catch (BusinessException ex) {
            rejected.incrementAndGet();
        }
    }

    private Harness memory() {
        AtomicReference<String> failure = new AtomicReference<>("none");
        InMemoryFinanceRepository repository = new InMemoryFinanceRepository(probe(failure));
        return harness(repository, failure);
    }

    private Harness jdbc() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:finance-pay-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        AtomicReference<String> failure = new AtomicReference<>("none");
        JdbcFinanceRepository repository = new JdbcFinanceRepository(dataSource, probe(failure));
        return harness(repository, failure);
    }

    private Harness harness(FinanceRepository repository, AtomicReference<String> failure) {
        FinanceCostService service = new FinanceCostService(repository);
        return new Harness() {
            @Override
            public void save(Payable payable) {
                repository.savePayable(payable);
            }

            @Override
            public PayablePayment pay(String payableCode, String amount, String key) {
                return service.pay(new PayablePaymentCommand(payableCode, new BigDecimal(amount), "2026-10-10", key), "财务");
            }

            @Override
            public void failAt(String point) {
                failure.set(point);
            }

            @Override
            public int paymentCount(String payableCode) {
                return (int) repository.listPayablePayments().stream()
                        .filter(payment -> payableCode.equals(payment.payableCode()))
                        .count();
            }

            @Override
            public BigDecimal paymentTotal(String payableCode) {
                return repository.listPayablePayments().stream()
                        .filter(payment -> payableCode.equals(payment.payableCode()))
                        .map(PayablePayment::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            @Override
            public BigDecimal paid(String payableCode) {
                return repository.findPayable(payableCode).orElseThrow().paidAmount();
            }
        };
    }

    private FinancePaymentProbe probe(AtomicReference<String> failure) {
        return new FinancePaymentProbe() {
            @Override
            public void beforeBalanceUpdate() {
                if ("balance".equals(failure.get())) {
                    throw new IllegalStateException("balance save failed");
                }
            }
        };
    }

    private Payable payable(String code, String amount, String paid) {
        return new Payable(
                code,
                "OUTSOURCE",
                "2026-10",
                "外协厂",
                "OUTSOURCE|100",
                null,
                "",
                new BigDecimal(amount),
                new BigDecimal(paid),
                "财务",
                "2026-10-10T00:00:00"
        );
    }

    private void createSchema(JdbcDataSource dataSource) throws Exception {
        String payable = """
                create table fin_payable (
                    code varchar(80) primary key,
                    kind varchar(20) not null,
                    year_month char(7) not null,
                    payee varchar(120) not null,
                    source_code varchar(160) not null,
                    customer_id bigint null,
                    mother_order_code varchar(80) not null,
                    amount decimal(18, 2) not null,
                    paid_amount decimal(18, 2) not null,
                    operator_name varchar(80) not null,
                    created_at varchar(40) not null
                )
                """;
        String payment = """
                create table fin_payable_payment (
                    code varchar(80) primary key,
                    payable_code varchar(80) not null,
                    amount decimal(18, 2) not null,
                    paid_on char(10) not null,
                    operator_name varchar(80) not null,
                    created_at varchar(40) not null
                )
                """;
        String migration;
        try (InputStream input = FinancePaymentContractTest.class.getResourceAsStream(
                "/db/mysql/V40__m3_finance_payment_idempotency.sql")) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(payable);
            statement.execute(payment);
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

    private interface Harness {
        void save(Payable payable);

        PayablePayment pay(String payableCode, String amount, String key);

        void failAt(String point);

        int paymentCount(String payableCode);

        BigDecimal paymentTotal(String payableCode);

        BigDecimal paid(String payableCode);
    }
}
