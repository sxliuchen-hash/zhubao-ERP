package com.zhubao.erp.business.infra;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MobileIntakeService;
import com.zhubao.erp.business.app.OrderMaintenanceCommands;
import com.zhubao.erp.business.app.OrderMaintenanceService;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.label.infra.JdbcPrintJobRepository;
import com.zhubao.erp.label.infra.JdbcPrintRoutingRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/** 在独立 MySQL 上核对母子单回收与单独打印恢复的锁顺序。内存和 H2 不能代替这项验证。 */
class A4MysqlRecycleConcurrencyTest {

    @Test
    void 母单回收会等待并重新读取并发恢复的子单() throws Exception {
        MysqlDataSource source = source();
        OrderMaintenanceService service = service(source);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String mother = intake(source, suffix);
        String child = "ZD-A4-" + suffix;
        insertChild(source, mother, child, suffix, true);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection holder = source.getConnection()) {
            holder.setAutoCommit(false);
            Future<?> lock = pool.submit(() -> {
                lockChild(holder, child);
                locked.countDown();
                release.await(20, TimeUnit.SECONDS);
                clearChildRecycle(holder, child);
                holder.commit();
                return null;
            });
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> recycle = pool.submit(() -> {
                service.recycle(new OrderMaintenanceCommands.RecycleCommand(
                        "MOTHER", mother, "并发核对", "a4-recycle-" + suffix), admin());
                return null;
            });
            try {
                assertThatThrownBy(() -> recycle.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            recycle.get(20, TimeUnit.SECONDS);
            lock.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(recycled(source, "biz_mother_order", mother)).isTrue();
        assertThat(recycled(source, "biz_work_order", child)).isTrue();
    }

    @Test
    void 单独恢复打印任务会先等母单锁再拒绝已回收母单() throws Exception {
        MysqlDataSource source = source();
        OrderMaintenanceService service = service(source);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String mother = intake(source, suffix);
        String printId = "P-A4-" + suffix;
        insertPrint(source, printId, mother, suffix);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection holder = source.getConnection()) {
            holder.setAutoCommit(false);
            Future<?> lock = pool.submit(() -> {
                lockMother(holder, mother);
                locked.countDown();
                release.await(20, TimeUnit.SECONDS);
                markMotherRecycled(holder, mother);
                holder.commit();
                return null;
            });
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> restore = pool.submit(() -> {
                service.restore(new OrderMaintenanceCommands.RestoreCommand(
                        "PRINT_JOB", printId, "a4-restore-print-" + suffix), admin());
                return null;
            });
            try {
                assertThatThrownBy(() -> restore.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThatThrownBy(() -> restore.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class)
                    .cause()
                    .hasMessageContaining("请先恢复对应单据");
            lock.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(recycled(source, "biz_mother_order", mother)).isTrue();
        assertThat(printRecycled(source, printId)).isTrue();
    }

    private OrderMaintenanceService service(MysqlDataSource source) {
        JdbcBusinessDocumentRepository documents = new JdbcBusinessDocumentRepository(source);
        StockLedgerService stock = new StockLedgerService(new JdbcStockLedgerRepository(source));
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source));
        return new OrderMaintenanceService(documents, new JdbcPrintJobRepository(source),
                new JdbcPrintRoutingRepository(source), new PermissionGuard(), unit, stock);
    }

    private String intake(MysqlDataSource source, String suffix) {
        JdbcBusinessDocumentRepository documents = new JdbcBusinessDocumentRepository(source);
        StockLedgerService stock = new StockLedgerService(new JdbcStockLedgerRepository(source));
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source));
        BusinessOperationService operations = new BusinessOperationService(stock, null, documents, unit);
        MobileIntakeService intake = new MobileIntakeService(operations, documents, unit, null, stock);
        return intake.intake(new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-10-20",
                "A4 回收并发", BigDecimal.ONE, new BigDecimal("2"), "", "AU9999", "件",
                "a4-recycle-intake-" + suffix, "CUSTOMER_DELIVERY", List.of()),
                new OperatorContext("A4 收货员", RoleCode.RECEIVER, DataScope.ALL)).motherOrderCode();
    }

    private void insertChild(MysqlDataSource source, String mother, String child, String suffix, boolean recycled)
            throws SQLException {
        String sql = """
                insert into biz_work_order (
                    code, mother_order_code, customer_id, customer_name, processing_requirement, due_date,
                    material_code, goods_name, quantity, quantity_unit, weight, warehouse_code, process_names,
                    verification_code, ledger_code, operator, created_at, recycled_at, recycled_by, recycle_batch,
                    recycle_reason
                )
                select ?, code, customer_id, customer_name, processing_requirement, due_date, material_code,
                       goods_name_snapshot, quantity, quantity_unit, weight, warehouse_code, '抛光', ?, '',
                       'A4', '2026-10-19T12:00', ?, 'A4', ?, ?
                  from biz_mother_order where code = ?
                """;
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, child);
            statement.setString(2, "V" + suffix);
            if (recycled) {
                statement.setString(3, "2026-10-19T12:00");
                statement.setString(4, "child-" + suffix);
                statement.setString(5, "先单独回收");
            } else {
                statement.setString(3, null);
                statement.setString(4, null);
                statement.setString(5, null);
            }
            statement.setString(6, mother);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void insertPrint(MysqlDataSource source, String printId, String mother, String suffix) throws SQLException {
        String sql = """
                insert into biz_print_job (
                    id, request_id, business_code, template_code, copies, content_snapshot, status_code,
                    station_id, lease_until, failure_reason, predecessor_id, retry_reason, created_by,
                    created_at, updated_at, recycled_at, recycled_by, recycle_batch, recycle_reason
                ) values (?, ?, ?, 'MOTHER', 1, '{}', 'PENDING', '', '', '', '', '', 'A4',
                          '2026-10-19T12:00', '2026-10-19T12:00', '2026-10-19T12:00', 'A4', ?, '先回收打印')
                """;
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, printId);
            statement.setString(2, "print-req-" + suffix);
            statement.setString(3, mother);
            statement.setString(4, "print-" + suffix);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void lockChild(Connection connection, String child) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select code from biz_work_order where code = ? for update")) {
            statement.setString(1, child);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private void clearChildRecycle(Connection connection, String child) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update biz_work_order
                   set recycled_at = null, recycled_by = null, recycle_batch = null, recycle_reason = null
                 where code = ?
                """)) {
            statement.setString(1, child);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void lockMother(Connection connection, String mother) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select code from biz_mother_order where code = ? for update")) {
            statement.setString(1, mother);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private void markMotherRecycled(Connection connection, String mother) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update biz_mother_order
                   set recycled_at = '2026-10-19T12:01', recycled_by = 'A4', recycle_batch = ?, recycle_reason = '并发回收'
                 where code = ?
                """)) {
            statement.setString(1, "mother-" + mother);
            statement.setString(2, mother);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private boolean recycled(MysqlDataSource source, String table, String code) throws SQLException {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select recycled_at from " + table + " where code = ?")) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getString(1) != null;
            }
        }
    }

    private boolean printRecycled(MysqlDataSource source, String printId) throws SQLException {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select recycled_at from biz_print_job where id = ?")) {
            statement.setString(1, printId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getString(1) != null;
            }
        }
    }

    private OperatorContext admin() {
        return new OperatorContext("A4 管理员", RoleCode.ADMIN, DataScope.ALL);
    }

    private MysqlDataSource source() throws SQLException {
        String url = System.getenv("A3_MYSQL_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "未指定独立 MySQL 校验库");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/a3_verify_[A-Za-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("A4 测试只允许连接本机 a3_verify_ 独立库");
        }
        String user = System.getenv("A3_MYSQL_DB_USER");
        String password = System.getenv("A3_MYSQL_DB_PASSWORD");
        if (user == null || user.isBlank() || password == null) {
            throw new IllegalArgumentException("A4 测试缺少独立库连接凭据");
        }
        MysqlDataSource source = new MysqlDataSource();
        source.setURL(url);
        source.setUser(user);
        source.setPassword(password);
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement("select max(version) from schema_migration");
             ResultSet result = statement.executeQuery()) {
            result.next();
            assertThat(result.getInt(1)).isGreaterThanOrEqualTo(63);
        }
        return source;
    }
}
