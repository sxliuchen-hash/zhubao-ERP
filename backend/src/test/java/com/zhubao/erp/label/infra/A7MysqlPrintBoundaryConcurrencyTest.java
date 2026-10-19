package com.zhubao.erp.label.infra;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.app.PrintBatchService;
import com.zhubao.erp.label.app.PrintJobService;
import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTarget;
import com.zhubao.erp.ops.app.DatabaseUpgradeService;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 失败和不确定任务的再打印门闩必须在独立 MySQL 上用行锁验证。内存和 H2 不能代替。 */
class A7MysqlPrintBoundaryConcurrencyTest {

    @Test
    void 未核纸的不确定任务会挡住并发新建批次且改派仍指向原批次() throws Exception {
        MysqlDataSource source = source();
        ensureSchema(source);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String stationA = "A" + suffix;
        String stationB = "B" + suffix;
        String business = "MD-A7-" + suffix;
        JdbcPrintRoutingRepository repository = new JdbcPrintRoutingRepository(source);
        PrintBatchService service = new PrintBatchService(repository,
                new PrintJobService(new InMemoryPrintJobRepository(), "unused"));
        AuthUserAccount account = account();
        String tokenA = service.saveStation(new PrintStation(stationA, "收货", true, "", "")).token();
        service.saveStation(new PrintStation(stationB, "包装", true, "", ""));
        service.saveQueue(new PrintQueue(stationA, "Q1", "原队列", "ALL", 40, 30, true, true));
        service.saveQueue(new PrintQueue(stationB, "Q2", "新队列", "ALL", 40, 30, true, true));
        PrintBatch original = service.create("a7-src-" + suffix, business, "FLOW", 1,
                List.of(new PrintTarget(stationA, "Q1")), account);
        String jobId = original.jobs().get(0).id();
        service.claim(stationA, tokenA);
        String expired = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().minusMinutes(3));
        repository.saveJob(repository.job(jobId).orElseThrow()
                .withStatus(PrintJobStatus.LEASED, stationA, expired, "", "", expired));
        assertThat(service.expireLeases(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))).isEqualTo(1);
        assertThat(repository.job(jobId).orElseThrow().status()).isEqualTo(PrintJobStatus.UNCERTAIN);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection holder = source.getConnection()) {
            holder.setAutoCommit(false);
            Future<?> lock = pool.submit(() -> {
                lockJob(holder, jobId);
                locked.countDown();
                release.await(20, TimeUnit.SECONDS);
                holder.commit();
                return null;
            });
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> create = pool.submit(() -> {
                service.create("a7-new-" + suffix, business, "FLOW", 1,
                        List.of(new PrintTarget(stationB, "Q2")), account);
                return null;
            });
            try {
                assertThatThrownBy(() -> create.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThatThrownBy(() -> create.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("请先核纸");
            lock.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count(source, "select count(*) from biz_print_batch where business_code = ?", business)).isEqualTo(1);
        assertThat(count(source, """
                select count(*) from biz_print_target_job j
                join biz_print_batch b on b.id = j.batch_id
                where b.business_code = ? and j.status_code = 'PENDING'
                """, business)).isZero();
        assertThatThrownBy(() -> service.claim(stationA, tokenA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有待打印任务");

        service.paperCheck(jobId, "确认未出纸", account);
        PrintBatch moved = service.reroute(jobId, "a7-reroute-" + suffix, new PrintTarget(stationB, "Q2"),
                "原工位未出纸", account);
        PrintBatchService.PrintLineage lineage = service.lineage(moved.jobs().get(0).id(), account);
        assertThat(lineage.sourceJobId()).isEqualTo(jobId);
        assertThat(lineage.sourceBatchId()).isEqualTo(original.id());
        assertThat(lineage.labelVersion()).isEqualTo(lineage.sourceLabelVersion());
        assertThat(repository.job(jobId).orElseThrow().status()).isEqualTo(PrintJobStatus.CANCELED);
        assertThat(repository.jobAny(jobId).orElseThrow().batchId()).isEqualTo(original.id());
    }

    private void lockJob(Connection connection, String jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from biz_print_target_job where id = ? for update")) {
            statement.setString(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private int count(MysqlDataSource source, String sql, String business) throws SQLException {
        try (Connection connection = source.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, business);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void ensureSchema(MysqlDataSource source) throws Exception {
        boolean ready;
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select count(*) from information_schema.tables
                     where table_schema = database() and table_name = 'schema_migration'
                     """)) {
            result.next();
            ready = result.getInt(1) == 1;
        }
        if (!ready) {
            new DatabaseUpgradeService(source, MigrationCatalog.loadClasspath()).upgrade(true);
        }
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement("select max(version) from schema_migration");
             ResultSet result = statement.executeQuery()) {
            result.next();
            assertThat(result.getInt(1)).isGreaterThanOrEqualTo(63);
        }
    }

    private AuthUserAccount account() {
        return new AuthUserAccount("operator", "hash", "操作员", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL,
                true, false, Instant.now(), 0, null, List.of(), false, false, List.of());
    }

    private MysqlDataSource source() throws SQLException {
        String url = System.getenv("A3_MYSQL_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "未指定独立 MySQL 校验库");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/a3_verify_[A-Za-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("A7 测试只允许连接本机 a3_verify_ 独立库");
        }
        String user = System.getenv("A3_MYSQL_DB_USER");
        String password = System.getenv("A3_MYSQL_DB_PASSWORD");
        if (user == null || user.isBlank() || password == null) {
            throw new IllegalArgumentException("A7 测试缺少独立库连接凭据");
        }
        MysqlDataSource source = new MysqlDataSource();
        source.setURL(url);
        source.setUser(user);
        source.setPassword(password);
        return source;
    }
}
