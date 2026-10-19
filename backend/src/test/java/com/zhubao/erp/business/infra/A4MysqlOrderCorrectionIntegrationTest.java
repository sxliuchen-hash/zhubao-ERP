package com.zhubao.erp.business.infra;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MobileIntakeService;
import com.zhubao.erp.business.app.OrderMaintenanceCommands;
import com.zhubao.erp.business.app.OrderMaintenanceService;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderAdjustment;
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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 仅对本机独立 a3_verify_ 数据库核验 A4 改单 JDBC 事务。 */
class A4MysqlOrderCorrectionIntegrationTest {

    @Test
    void 真实MySQL中调整记录失败使母单字段回滚() throws SQLException {
        MysqlDataSource source = source();
        JdbcBusinessDocumentRepository documents = new JdbcBusinessDocumentRepository(source);
        StockLedgerService stock = new StockLedgerService(new JdbcStockLedgerRepository(source));
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source));
        BusinessOperationService operations = new BusinessOperationService(stock, null, documents, unit);
        MobileIntakeService intake = new MobileIntakeService(operations, documents, unit, null, stock);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String motherCode = intake.intake(new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-10-20",
                "A4 原货品", BigDecimal.ONE, new BigDecimal("2"), "", "AU9999", "件", "a4-intake-" + suffix,
                "CUSTOMER_DELIVERY", List.of()),
                new OperatorContext("A4 收货员", RoleCode.RECEIVER, DataScope.ALL)).motherOrderCode();
        MotherOrderRecord current = documents.findMotherOrderAny(motherCode).orElseThrow();
        MotherOrderRecord next = new MotherOrderRecord(current.code(), current.customerId(), current.ownerCustomerId(),
                current.materialCode(), current.inboundDate(), current.expressCompany(), current.courierNo(),
                current.senderName(), current.receiverName(), current.unpackSigner(), "A4 改正货品", current.ownerType(),
                current.customerOwned(), current.warehouseCode(), current.quantity(), current.quantityUnit(),
                current.weight(), current.stockStatus(), current.ledgerCode(), current.sourceRegisterType(),
                current.sourceRegisterLineNo(), current.sourceRegisterSequence(), current.operator(),
                current.evidenceCode(), current.remark(), current.status(), current.createdAt(),
                current.processingRequirement(), current.dueDate(), current.customerName());
        String failReason = "A4_FAIL_" + suffix;
        String constraint = "chk_a4_fail_" + suffix;
        boolean installed = false;
        try {
            execute(source, "alter table biz_order_adjustment add constraint " + constraint
                    + " check (reason <> '" + failReason + "')");
            installed = true;
            assertThatThrownBy(() -> documents.correctMotherOrder(next, adjustment(motherCode, failReason)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(goodsName(source, motherCode)).isEqualTo(current.goodsName());
            assertThat(adjustmentCount(source, motherCode)).isZero();
        } finally {
            if (installed) execute(source, "alter table biz_order_adjustment drop check " + constraint);
        }

        documents.correctMotherOrder(next, adjustment(motherCode, "A4 已核对"));
        assertThat(goodsName(source, motherCode)).isEqualTo("A4 改正货品");
        assertThat(adjustmentCount(source, motherCode)).isEqualTo(1);

        OrderMaintenanceService service = new OrderMaintenanceService(documents, new JdbcPrintJobRepository(source),
                new JdbcPrintRoutingRepository(source), new PermissionGuard(), unit);
        OperatorContext admin = new OperatorContext("A4 管理员", RoleCode.ADMIN, DataScope.ALL);
        String revision = service.search(motherCode, admin).get(0).revision();
        String retryReason = "A4_RETRY_FAIL_" + suffix;
        String retryConstraint = "chk_a4_retry_" + suffix;
        String requestId = "a4-retry-" + suffix;
        var retry = new OrderMaintenanceCommands.MotherCorrection(null, "恒信珠宝", "AU9999", "A4 事务重试货品",
                BigDecimal.ONE, new BigDecimal("2"), "抛光", "2026-10-20", "", retryReason,
                requestId, revision);
        installed = false;
        try {
            execute(source, "alter table biz_order_adjustment add constraint " + retryConstraint
                    + " check (reason <> '" + retryReason + "')");
            installed = true;
            assertThatThrownBy(() -> service.correctMother(motherCode, retry, admin))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(goodsName(source, motherCode)).isEqualTo("A4 改正货品");
            assertThat(adjustmentCount(source, motherCode)).isEqualTo(1);
        } finally {
            if (installed) execute(source, "alter table biz_order_adjustment drop check " + retryConstraint);
        }
        service.correctMother(motherCode, retry, admin);
        service.correctMother(motherCode, retry, admin);
        assertThat(goodsName(source, motherCode)).isEqualTo("A4 事务重试货品");
        assertThat(adjustmentCount(source, motherCode)).isEqualTo(2);
    }

    @Test
    void 真实MySQL中并发旧快照仅提交一次且同请求回放() throws Exception {
        MysqlDataSource source = source();
        JdbcBusinessDocumentRepository documents = new JdbcBusinessDocumentRepository(source);
        StockLedgerService stock = new StockLedgerService(new JdbcStockLedgerRepository(source));
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source));
        BusinessOperationService operations = new BusinessOperationService(stock, null, documents, unit);
        MobileIntakeService intake = new MobileIntakeService(operations, documents, unit, null, stock);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String motherCode = intake.intake(new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-10-20",
                "A4 并发原货品", BigDecimal.ONE, new BigDecimal("2"), "", "AU9999", "件", "a4-con-intake-" + suffix,
                "CUSTOMER_DELIVERY", List.of()),
                new OperatorContext("A4 收货员", RoleCode.RECEIVER, DataScope.ALL)).motherOrderCode();
        OrderMaintenanceService service = new OrderMaintenanceService(documents, new JdbcPrintJobRepository(source),
                new JdbcPrintRoutingRepository(source), new PermissionGuard(), unit);
        OperatorContext admin = new OperatorContext("A4 管理员", RoleCode.ADMIN, DataScope.ALL);
        String revision = service.search(motherCode, admin).get(0).revision();
        var first = correction("A4 改正甲", "a4-correct-a-" + suffix, revision);
        var second = correction("A4 改正乙", "a4-correct-b-" + suffix, revision);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        boolean firstSuccess;
        boolean secondSuccess;
        try {
            Future<Boolean> firstResult = pool.submit(() -> correctAfterStart(service, motherCode, first, admin, start));
            Future<Boolean> secondResult = pool.submit(() -> correctAfterStart(service, motherCode, second, admin, start));
            start.countDown();
            firstSuccess = firstResult.get();
            secondSuccess = secondResult.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(List.of(firstSuccess, secondSuccess)).containsExactlyInAnyOrder(true, false);
        assertThat(adjustmentCount(source, motherCode)).isEqualTo(1);
        service.correctMother(motherCode, firstSuccess ? first : second, admin);
        assertThat(adjustmentCount(source, motherCode)).isEqualTo(1);
        assertThatThrownBy(() -> service.correctMother(motherCode, firstSuccess ? second : first, admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已被修改");
    }

    private boolean correctAfterStart(OrderMaintenanceService service, String code,
                                      OrderMaintenanceCommands.MotherCorrection command, OperatorContext admin,
                                      CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.correctMother(code, command, admin);
            return true;
        } catch (BusinessException ex) {
            assertThat(ex).hasMessageContaining("已被修改");
            return false;
        }
    }

    private OrderMaintenanceCommands.MotherCorrection correction(String goodsName, String requestId, String revision) {
        return new OrderMaintenanceCommands.MotherCorrection(null, "恒信珠宝", "AU9999", goodsName,
                BigDecimal.ONE, new BigDecimal("2"), "抛光", "2026-10-20", "", "并发核对", requestId, revision);
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
        return source;
    }

    private OrderAdjustment adjustment(String motherCode, String reason) {
        return new OrderAdjustment("MOTHER", motherCode, "原货品", "改正货品", reason, "A4 管理员",
                "2026-10-19T12:00");
    }

    private String goodsName(DataSource source, String code) throws SQLException {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select goods_name_snapshot from biz_mother_order where code = ?")) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private int adjustmentCount(DataSource source, String code) throws SQLException {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from biz_order_adjustment where target_type = 'MOTHER' and target_code = ?")) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void execute(DataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
