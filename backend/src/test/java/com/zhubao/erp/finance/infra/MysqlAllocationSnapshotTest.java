package com.zhubao.erp.finance.infra;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只在显式给出的 MySQL 8.4 测试服务器上验证可重复读。
 * 每次创建 UUID 隔离库，不使用业务库。H2 不能代替这项验证。
 */
@EnabledIfSystemProperty(named = "zhubao.test.mysql.url", matches = ".+")
class MysqlAllocationSnapshotTest {

    private MysqlDataSource server;
    private MysqlDataSource database;
    private String databaseName;
    private JdbcFinanceRepository repository;

    @BeforeEach
    void createIsolatedDatabase() throws Exception {
        String url = System.getProperty("zhubao.test.mysql.url");
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("测试地址必须使用 jdbc:mysql://");
        }
        URI uri = URI.create(url.substring("jdbc:".length()));
        String serverUrl = "jdbc:mysql://" + uri.getRawAuthority() + "/";
        String options = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        server = dataSource(serverUrl + options);
        databaseName = "zhubao_alloc_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = server.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            statement.execute("CREATE DATABASE `" + databaseName + "` CHARACTER SET utf8mb4");
        }
        database = dataSource(serverUrl + databaseName + options);
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(8);
            try (var isolation = statement.executeQuery("select @@transaction_isolation")) {
                assertThat(isolation.next()).isTrue();
                assertThat(isolation.getString(1)).isEqualToIgnoringCase("REPEATABLE-READ");
            }
            statement.execute("""
                    create table fin_receivable_line (
                        code varchar(80) primary key,
                        customer_id bigint not null,
                        customer_name varchar(120) not null,
                        year_month char(7) not null,
                        source_type varchar(40) not null,
                        source_key varchar(160) not null,
                        mother_order_code varchar(80) not null,
                        work_order_code varchar(80) not null,
                        process_name varchar(80) not null,
                        quantity decimal(18, 3) not null,
                        charge_unit varchar(20) not null,
                        unit_price decimal(18, 4) not null,
                        amount decimal(18, 2) not null,
                        reverses_code varchar(80) not null,
                        remark varchar(400) not null,
                        operator_name varchar(80) not null,
                        created_at varchar(40) not null
                    )
                    """);
            statement.execute("""
                    create table fin_customer_receipt (
                        code varchar(80) primary key,
                        customer_id bigint not null,
                        customer_name varchar(120) not null,
                        year_month char(7) not null,
                        amount decimal(18, 2) not null,
                        received_on char(10) not null,
                        operator_name varchar(80) not null,
                        created_at varchar(40) not null,
                        idempotency_key varchar(80) null,
                        unique key uk_fin_receipt_idem (idempotency_key)
                    )
                    """);
            statement.execute("""
                    create table fin_receipt_allocation (
                        receipt_code varchar(80) not null,
                        line_no int not null,
                        receivable_code varchar(80) not null,
                        amount decimal(18, 2) not null,
                        primary key (receipt_code, line_no),
                        key idx_fin_receipt_alloc_receivable (receivable_code)
                    )
                    """);
            statement.execute("""
                    create table fin_receipt_allocation_operation (
                        code varchar(80) primary key,
                        receipt_code varchar(80) not null,
                        customer_id bigint not null,
                        action_code varchar(20) not null,
                        reason varchar(400) not null,
                        operator_name varchar(80) not null,
                        request_id varchar(80) not null,
                        created_at varchar(40) not null
                    )
                    """);
            statement.execute("""
                    create table fin_receipt_allocation_operation_line (
                        operation_code varchar(80) not null,
                        line_no int not null,
                        receivable_code varchar(80) not null,
                        amount decimal(18, 2) not null,
                        reverses_ref varchar(160) not null,
                        primary key (operation_code, line_no)
                    )
                    """);
        }
        repository = new JdbcFinanceRepository(database);
        repository.saveReceivable(new ReceivableLine(
                "AR-RACE", 1L, "客户", "2026-10", "FIXTURE", "FIX-AR-RACE", "MD-RACE", "AR-RACE", "",
                BigDecimal.ONE, ChargeUnit.PIECE, BigDecimal.ONE, new BigDecimal("80.00"), "", "夹具", "财务",
                "2026-10-01T00:00:00"));
    }

    @AfterEach
    void dropIsolatedDatabase() throws Exception {
        if (databaseName != null) {
            try (Connection connection = server.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + databaseName + "`");
            }
        }
    }

    @Test
    void waitingTransactionRereadsCommittedAllocationUnderLock() throws Exception {
        CountDownLatch snapshotOpened = new CountDownLatch(1);
        CountDownLatch otherCommitted = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        JdbcFinanceRepository racing = new JdbcFinanceRepository(database, null, new com.zhubao.erp.finance.domain.FinanceReceiptProbe() {
            @Override
            public void beforeAllocationInsert() {
            }

            @Override
            public void afterIdempotencyRead() {
                if (reads.incrementAndGet() != 1) {
                    return;
                }
                snapshotOpened.countDown();
                try {
                    if (!otherCommitted.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待另一笔核销提交超时");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待被中断", ex);
                }
            }
        });
        Thread first = new Thread(() -> {
            try {
                racing.registerReceipt(receipt("RC-WAIT", "80.00", "race-wait-0001"), "race-wait-0001");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        first.start();
        assertThat(snapshotOpened.await(15, TimeUnit.SECONDS)).isTrue();
        repository.registerReceipt(receipt("RC-OTHER", "80.00", "race-other-0001"), "race-other-0001");
        otherCommitted.countDown();
        first.join(15000);
        assertThat(failure.get()).isInstanceOf(BusinessException.class).hasMessageContaining("不能超过尚未核销的余额");
        assertThat(repository.listReceipts()).hasSize(1);
        BigDecimal allocated = repository.listReceipts().get(0).allocations().stream()
                .map(ReceiptAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo("80.00");
    }

    private static CustomerReceipt receipt(String code, String amount, String key) {
        return new CustomerReceipt(code, 1L, "客户", "2026-10", new BigDecimal(amount), "2026-10-27",
                List.of(new ReceiptAllocation("AR-RACE", new BigDecimal(amount))), "财务", "2026-10-27T00:00:00");
    }

    private static MysqlDataSource dataSource(String url) {
        MysqlDataSource source = new MysqlDataSource();
        source.setURL(url);
        source.setUser(System.getProperty("zhubao.test.mysql.user", "root"));
        source.setPassword(System.getProperty("zhubao.test.mysql.password", ""));
        return source;
    }
}
