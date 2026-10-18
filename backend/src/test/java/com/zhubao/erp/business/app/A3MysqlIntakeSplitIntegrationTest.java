package com.zhubao.erp.business.app;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeLine;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.domain.SplitSourceAllocation;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.business.infra.JdbcBusinessDocumentRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 仅对本机独立 a3_verify_ 数据库执行真实 MySQL 事务核验。 */
class A3MysqlIntakeSplitIntegrationTest {

    @Test
    void 逐行收货和多来源拆单在真实MySQL回滚重试与对账() throws Exception {
        MysqlDataSource dataSource = dataSource();
        assertThat(queryNumber(dataSource, "select max(version) from schema_migration", null)).isEqualTo(63);

        JdbcBusinessDocumentRepository documents = new JdbcBusinessDocumentRepository(dataSource);
        JdbcStockLedgerRepository stock = new JdbcStockLedgerRepository(dataSource);
        StockLedgerService ledgers = new StockLedgerService(stock);
        JdbcCommandRequestRepository requests = new JdbcCommandRequestRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests);
        BusinessOperationService operations = new BusinessOperationService(ledgers, null, documents, unit);
        MobileIntakeService intake = new MobileIntakeService(operations, documents, unit, null, ledgers);
        OperatorContext receiver = new OperatorContext("A3 收货员", RoleCode.RECEIVER, DataScope.ALL);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String mother = intake.intake(new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-10-20",
                "戒托", new BigDecimal("8"), new BigDecimal("12"), "", "AU9999", "件", "a3-intake-" + suffix,
                "CUSTOMER_DELIVERY", List.of(
                new MobileIntakeLine("AU9999", "金戒托", new BigDecimal("3"), "件", new BigDecimal("4")),
                new MobileIntakeLine("PT950", "铂金戒托", new BigDecimal("5"), "件", new BigDecimal("8")))),
                receiver).motherOrderCode();
        assertThat(queryNumber(dataSource,
                "select count(*) from biz_mother_order_item where mother_order_code = ?", mother)).isEqualTo(1);
        assertThat(queryNumber(dataSource,
                "select count(*) from biz_receipt_line where mother_order_code = ?", mother)).isEqualTo(2);
        assertThat(queryNumber(dataSource,
                "select count(*) from inv_stock_ledger where mother_order_code = ?", mother)).isEqualTo(2);

        MotherOrderSplitService splits = new MotherOrderSplitService(documents, ledgers, unit);
        SplitMotherOrderCommand command = new SplitMotherOrderCommand(mother, receiver.operator(), List.of(
                new SplitMotherOrderLine(new BigDecimal("2"), new BigDecimal("2"), List.of("抛光"),
                        List.of(new SplitSourceAllocation(1, new BigDecimal("2"), new BigDecimal("2")))),
                new SplitMotherOrderLine(new BigDecimal("2"), new BigDecimal("3"), List.of("抛光"),
                        List.of(new SplitSourceAllocation(2, new BigDecimal("2"), new BigDecimal("3"))))));
        String constraint = "chk_a3_fail_" + suffix;
        boolean constraintInstalled = false;
        try {
            execute(dataSource, "alter table biz_child_line_allocation add constraint " + constraint
                    + " check (mother_order_code <> '" + mother + "' or source_line_no <> 2)");
            constraintInstalled = true;
            assertThatThrownBy(() -> splits.split(command, "a3-split-" + suffix))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("子单来源分配保存失败");
            assertThat(documents.listChildWorkOrders(mother)).isEmpty();
            assertThat(documents.listChildAllocations(mother, null)).isEmpty();
            assertThat(documents.findMotherSplitState(mother)).isEmpty();
            assertThat(queryNumber(dataSource,
                    "select count(*) from inv_stock_ledger where mother_order_code = ?", mother)).isEqualTo(2);
            assertThat(stockQuantity(dataSource, mother, "RAW_WAITING")).isEqualByComparingTo("8");
            assertThat(stockQuantity(dataSource, mother, "WIP")).isEqualByComparingTo("0");
        } finally {
            if (constraintInstalled) execute(dataSource,
                    "alter table biz_child_line_allocation drop check " + constraint);
        }

        var created = splits.split(command, "a3-split-" + suffix);
        assertThat(created.children()).hasSize(2);
        assertThat(created.sourceLines()).extracting(line -> line.remainingQuantity())
                .containsExactly(new BigDecimal("1.000"), new BigDecimal("3.000"));
        assertThat(documents.listChildAllocations(mother, null)).hasSize(2);
        assertThat(queryNumber(dataSource,
                "select count(*) from inv_stock_ledger where mother_order_code = ?", mother)).isEqualTo(4);
        assertThat(stockQuantity(dataSource, mother, "RAW_WAITING")).isEqualByComparingTo("4");
        assertThat(stockQuantity(dataSource, mother, "WIP")).isEqualByComparingTo("4");
        assertThat(splits.split(command, "a3-split-" + suffix).children()).hasSize(2);
        assertThat(queryNumber(dataSource,
                "select count(*) from inv_stock_ledger where mother_order_code = ?", mother)).isEqualTo(4);
        assertThat(queryNumber(dataSource, """
                select count(*) from biz_child_line_allocation a
                left join biz_work_order w on w.code = a.child_order_code
                left join inv_stock_ledger l on l.code = a.ledger_code
                where a.mother_order_code = ? and (w.code is null or l.code is null
                     or l.quantity <> a.quantity or l.weight <> a.weight)
                """, mother)).isZero();
    }

    @Test
    void 发货旧明细失败时真实MySQL回滚并可用原请求重试() throws Exception {
        MysqlDataSource dataSource = dataSource();
        JdbcBusinessDocumentRepository documents = new JdbcBusinessDocumentRepository(dataSource);
        JdbcStockLedgerRepository stock = new JdbcStockLedgerRepository(dataSource);
        StockLedgerService ledgers = new StockLedgerService(stock);
        JdbcCommandRequestRepository requests = new JdbcCommandRequestRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests);
        BusinessOperationService operations = new BusinessOperationService(ledgers, null, documents, unit);
        MobileIntakeService intake = new MobileIntakeService(operations, documents, unit, null, ledgers);
        OperatorContext receiver = new OperatorContext("A3 收货员", RoleCode.RECEIVER, DataScope.ALL);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String mother = intake.intake(new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-10-20",
                "戒托", new BigDecimal("2"), new BigDecimal("3"), "", "AU9999", "件", "a3-out-intake-" + suffix,
                "CUSTOMER_DELIVERY", List.of()), receiver).motherOrderCode();
        MotherOrderSplitService splits = new MotherOrderSplitService(documents, ledgers, unit);
        var split = splits.split(new SplitMotherOrderCommand(mother, receiver.operator(), List.of(
                new SplitMotherOrderLine(BigDecimal.ONE, new BigDecimal("1.5"), List.of("抛光"), List.of()),
                new SplitMotherOrderLine(BigDecimal.ONE, new BigDecimal("1.5"), List.of("抛光"), List.of()))),
                "a3-out-split-" + suffix);
        var child = documents.findChildWorkOrderByCode(split.children().get(0).code()).orElseThrow();
        StockDimension wip = new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, mother, child.code(),
                child.materialCode(), child.warehouseCode(), StockStatus.WIP);
        StockDimension finished = new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, mother, child.code(),
                child.materialCode(), "FINISHED-CUSTOMER", StockStatus.FINISHED);
        ledgers.transfer(new StockTransferCommand("quality_release", "QC-A3-" + suffix, wip, finished,
                child.quantity(), child.weight(), "A3 隔离样本转入成品"));
        String shipment = "OUT-A3-" + suffix;
        ShipFinishedStockCommand command = new ShipFinishedStockCommand(
                shipment, mother, child.code(), 1001L, null, child.materialCode(),
                "FINISHED-CUSTOMER", "SHIPPED-CUSTOMER", "2026-10-19", "成品",
                child.quantity(), child.quantityUnit(), child.weight(), "顺丰", "SF-A3-" + suffix,
                "", "A3_TEST", 1, "1", "A3 操作员", receiver.operator(), "", "A3 出库核验",
                "a3-out-shipment-" + suffix);
        String constraint = "chk_a3_out_" + suffix;
        boolean constraintInstalled = false;
        try {
            execute(dataSource, "alter table biz_outbound_order_item add constraint " + constraint
                    + " check (outbound_order_code <> '" + shipment + "')");
            constraintInstalled = true;
            assertThatThrownBy(() -> operations.shipFinishedStock(command, receiver.operator()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to save outbound order");
            assertThat(queryNumber(dataSource,
                    "select count(*) from biz_outbound_order where code = ?", shipment)).isZero();
            assertThat(queryNumber(dataSource,
                    "select count(*) from biz_outbound_order_item where outbound_order_code = ?", shipment)).isZero();
            assertThat(queryNumber(dataSource,
                    "select count(*) from inv_stock_ledger where document_type = 'outbound_shipment' and document_code = ?",
                    shipment)).isZero();
            assertThat(stockQuantity(dataSource, mother, "FINISHED")).isEqualByComparingTo("1");
            assertThat(stockQuantity(dataSource, mother, "SHIPPED")).isEqualByComparingTo("0");
        } finally {
            if (constraintInstalled) execute(dataSource,
                    "alter table biz_outbound_order_item drop check " + constraint);
        }

        var shipped = operations.shipFinishedStock(command, receiver.operator());
        assertThat(shipped.shipmentCode()).isEqualTo(shipment);
        assertThat(queryNumber(dataSource,
                "select count(*) from biz_outbound_order where code = ?", shipment)).isEqualTo(1);
        assertThat(queryNumber(dataSource,
                "select count(*) from biz_outbound_order_item where outbound_order_code = ?", shipment)).isEqualTo(1);
        assertThat(queryNumber(dataSource,
                "select count(*) from inv_stock_ledger where document_type = 'outbound_shipment' and document_code = ?",
                shipment)).isEqualTo(1);
        assertThat(stockQuantity(dataSource, mother, "FINISHED")).isEqualByComparingTo("0");
        assertThat(stockQuantity(dataSource, mother, "SHIPPED")).isEqualByComparingTo("1");
        assertThat(operations.shipFinishedStock(command, receiver.operator()).ledgerCode())
                .isEqualTo(shipped.ledgerCode());
        assertThat(queryNumber(dataSource,
                "select count(*) from biz_outbound_order_item where outbound_order_code = ?", shipment)).isEqualTo(1);
        assertThat(queryNumber(dataSource, """
                select count(*) from biz_outbound_order_item i
                join biz_outbound_order o on o.code = i.outbound_order_code
                join inv_stock_ledger l on l.code = i.ledger_code
                where i.outbound_order_code = ? and (i.quantity <> o.delivery_quantity
                    or i.weight <> o.delivery_weight or i.ledger_code <> o.ledger_code
                    or l.quantity <> i.quantity or l.weight <> i.weight)
                """, shipment)).isZero();
    }

    private MysqlDataSource dataSource() throws SQLException {
        String url = System.getenv("A3_MYSQL_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "未指定独立 MySQL 校验库");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/a3_verify_[A-Za-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("A3 测试只允许连接本机 a3_verify_ 独立库");
        }
        String user = System.getenv("A3_MYSQL_DB_USER");
        String password = System.getenv("A3_MYSQL_DB_PASSWORD");
        if (user == null || user.isBlank() || password == null) {
            throw new IllegalArgumentException("A3 测试缺少独立库连接凭据");
        }
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        assertThat(queryNumber(dataSource, "select max(version) from schema_migration", null)).isEqualTo(63);
        return dataSource;
    }

    private int queryNumber(DataSource dataSource, String sql, String code) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (code != null) statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private BigDecimal stockQuantity(DataSource dataSource, String mother, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select coalesce(sum(available_quantity), 0) from inv_stock_balance
                      where mother_order_code = ? and stock_status = ?
                     """)) {
            statement.setString(1, mother);
            statement.setString(2, status);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private void execute(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
