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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 核对旧单缺失流水编号时，按母单范围识别实际库存流水的 SQL 与索引。 */
class A4MysqlPostedFactQueryTest {

    @Test
    void 发货单号流水仍锁定母单和子单且走母单索引() throws Exception {
        MysqlDataSource source = source();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String mother = intake(source, suffix);
        String child = "ZD-A4Q-" + suffix;
        String shipment = "OUT-" + suffix;
        insertChild(source, mother, child, suffix);
        insertShipmentLedger(source, mother, child, shipment, suffix);
        try (Connection connection = source.getConnection();
             PreparedStatement clear = connection.prepareStatement(
                     "delete from inv_stock_ledger where mother_order_code = ? and document_code <> ?")) {
            clear.setString(1, mother);
            clear.setString(2, shipment);
            clear.executeUpdate();
        }
        try (Connection connection = source.getConnection();
             PreparedStatement blank = connection.prepareStatement(
                     "update biz_mother_order set ledger_code = '' where code = ?")) {
            blank.setString(1, mother);
            assertThat(blank.executeUpdate()).isEqualTo(1);
        }
        OrderMaintenanceService service = service(source);
        OperatorContext admin = new OperatorContext("A4 管理员", RoleCode.ADMIN, DataScope.ALL);
        var motherView = service.search(mother, admin).stream()
                .filter(row -> "MOTHER".equals(row.targetType())).findFirst().orElseThrow();
        var childView = service.search(child, admin).stream()
                .filter(row -> "WORK_ORDER".equals(row.targetType())).findFirst().orElseThrow();
        assertThat(motherView.postedFactsLocked()).isTrue();
        assertThat(childView.postedFactsLocked()).isTrue();
        assertThatThrownBy(() -> service.correctMother(mother, new OrderMaintenanceCommands.MotherCorrection(
                null, "恒信珠宝", "AU9999", "A4 查询货品", new BigDecimal("3"), new BigDecimal("2"),
                "抛光", "2026-10-20", "", "数量核对", "a4-query-mother-" + suffix, motherView.revision()), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
        assertThatThrownBy(() -> service.correctChild(child, new OrderMaintenanceCommands.ChildCorrection(
                null, "恒信珠宝", "AU9999", "A4 查询货品", BigDecimal.ONE, new BigDecimal("2"),
                "抛光", "2026-10-20", "镶嵌", "工序核对", "a4-query-child-" + suffix, childView.revision()), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
        assertThat(explainKey(source, mother, child)).isEqualTo("idx_stock_ledger_mother_time");
    }

    private String explainKey(MysqlDataSource source, String mother, String child) throws SQLException {
        String sql = """
                explain select 1 from inv_stock_ledger
                 where mother_order_code = ?
                   and (document_code = ? or work_order_code = ? or from_work_order_code = ?)
                 limit 1
                """;
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mother);
            statement.setString(2, child);
            statement.setString(3, child);
            statement.setString(4, child);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString("key");
            }
        }
    }

    private void insertShipmentLedger(MysqlDataSource source, String mother, String child, String shipment, String suffix)
            throws SQLException {
        String sql = """
                insert into inv_stock_ledger (
                    code, biz_time, document_type, document_code, owner_type, owner_customer_id, customer_id,
                    mother_order_code, work_order_code, from_work_order_code, material_code, quantity, weight,
                    from_warehouse_code, to_warehouse_code, from_stock_status, to_stock_status, remark
                )
                select ?, biz_time, 'outbound_shipment', ?, owner_type, owner_customer_id, customer_id,
                       mother_order_code, ?, null, material_code, quantity, weight,
                       from_warehouse_code, to_warehouse_code, from_stock_status, to_stock_status, '历史发货'
                  from inv_stock_ledger where mother_order_code = ? limit 1
                """;
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "STK-A4Q-" + suffix);
            statement.setString(2, shipment);
            statement.setString(3, child);
            statement.setString(4, mother);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void insertChild(MysqlDataSource source, String mother, String child, String suffix) throws SQLException {
        String sql = """
                insert into biz_work_order (
                    code, mother_order_code, customer_id, customer_name, processing_requirement, due_date,
                    material_code, goods_name, quantity, quantity_unit, weight, warehouse_code, process_names,
                    verification_code, ledger_code, operator, created_at
                )
                select ?, code, customer_id, customer_name, processing_requirement, due_date, material_code,
                       goods_name_snapshot, quantity, quantity_unit, weight, warehouse_code, '抛光', ?, '',
                       'A4', '2026-10-19T12:00'
                  from biz_mother_order where code = ?
                """;
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, child);
            statement.setString(2, "Q" + suffix);
            statement.setString(3, mother);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
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
                "A4 查询货品", BigDecimal.ONE, new BigDecimal("2"), "", "AU9999", "件",
                "a4-query-intake-" + suffix, "CUSTOMER_DELIVERY", List.of()),
                new OperatorContext("A4 收货员", RoleCode.RECEIVER, DataScope.ALL)).motherOrderCode();
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
}
