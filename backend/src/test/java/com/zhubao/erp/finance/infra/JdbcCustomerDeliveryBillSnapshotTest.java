package com.zhubao.erp.finance.infra;

import com.zhubao.erp.finance.domain.CustomerBillSnapshot;
import com.zhubao.erp.finance.domain.CustomerChargeLine;
import com.zhubao.erp.finance.domain.CustomerChargeMethod;
import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillKind;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillLine;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.domain.WorkSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCustomerDeliveryBillSnapshotTest {

    @Test
    void 新仓储仍能按已提交快照复算() throws Exception {
        JdbcDataSource dataSource = database();
        String billId = "CB" + "a".repeat(32);
        CustomerDeliveryBill bill = new CustomerDeliveryBill(
                billId, "rf15-jdbc1", billId, "FH-20261030-1501", 7L,
                CustomerDeliveryBillKind.INITIAL, new BigDecimal("17.00"), 1);
        CustomerChargeLine input = new CustomerChargeLine(
                "加工费", "MD-20261030-1001", CustomerChargeMethod.BASE_MINUS_QUANTITY, "颗",
                null, new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("1.5"), null, null, "按基数扣免计");
        CustomerDeliveryBillLine line = new CustomerDeliveryBillLine(
                billId, 1, "加工费", CustomerChargeMethod.BASE_MINUS_QUANTITY, new BigDecimal("17.00"),
                "（10颗 − 1.5颗）× 2元／颗＝17元", "MD-20261030-1001", "颗", null,
                null, new BigDecimal("2.0000"), new BigDecimal("10.000"), new BigDecimal("1.500"), null,
                new BigDecimal("8.500"), "按基数扣免计", new BigDecimal("10.000"), null, "颗",
                3, 4, 2, "HALF_UP");
        JdbcCustomerDeliveryBillRepository writer = new JdbcCustomerDeliveryBillRepository(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            writer.save(bill, List.of(line), session(connection));
            connection.commit();
        }
        JdbcCustomerDeliveryBillRepository reader = new JdbcCustomerDeliveryBillRepository(dataSource);
        List<CustomerDeliveryBillLine> loaded = reader.lines(billId);
        assertThat(loaded).singleElement().satisfies(stored -> {
            assertThat(stored.exemptQuantity()).isEqualByComparingTo("1.500");
            assertThat(stored.direction()).isNull();
            assertThat(stored.basis()).isEqualTo("按基数扣免计");
        });
        assertThat(CustomerBillSnapshot.recompute("FH-20261030-1501", loaded).totalAmount()).isEqualByComparingTo("17.00");
        assertThat(input.exemptQuantity()).isEqualByComparingTo(loaded.get(0).exemptQuantity());
    }

    @Test
    void 混合单位来源读回可复算且来源与账单一起回滚() throws Exception {
        JdbcDataSource dataSource = database();
        String billId = "CB" + "b".repeat(32);
        var sourceRows = List.of(new com.zhubao.erp.finance.domain.CustomerChargeSource("MD-20261030-1001", "颗", new BigDecimal("10")),
                new com.zhubao.erp.finance.domain.CustomerChargeSource("MD-20261030-1002", "件", new BigDecimal("2")));
        var input = new CustomerChargeLine("整单加工费", "", CustomerChargeMethod.AGREED_AMOUNT, "", null, null, null, null,
                new BigDecimal("100"), null, "本次议定");
        var snapshot = com.zhubao.erp.finance.domain.CustomerChargeCalculator.calculate(
                new com.zhubao.erp.finance.domain.CustomerChargeCoverage("FH-20261030-5001", null, "", null, sourceRows), List.of(input));
        var repository = new JdbcCustomerDeliveryBillRepository(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            repository.save(new CustomerDeliveryBill(billId, "rf50-jdbc-ok", billId, "FH-20261030-5001", 7L, CustomerDeliveryBillKind.INITIAL,
                    snapshot.totalAmount(), 1), List.of(CustomerDeliveryBillLine.capture(billId, 1, input, snapshot.lines().get(0), snapshot)), session(connection));
            connection.commit();
        }
        var reader = new JdbcCustomerDeliveryBillRepository(dataSource);
        assertThat(reader.lines(billId).get(0).coverageSources()).isEqualTo(sourceRows);
        assertThat(CustomerBillSnapshot.recompute("FH-20261030-5001", reader.lines(billId)).totalAmount()).isEqualByComparingTo("100");
        String rollback = "CB" + "c".repeat(32);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            repository.save(new CustomerDeliveryBill(rollback, "rf50-jdbc-no", rollback, "FH-20261030-5002", 7L, CustomerDeliveryBillKind.INITIAL,
                    snapshot.totalAmount(), 1), List.of(CustomerDeliveryBillLine.capture(rollback, 1, input, snapshot.lines().get(0), snapshot)), session(connection));
            connection.rollback();
        }
        assertThat(reader.find(rollback)).isEmpty();
        assertThat(reader.lines(rollback)).isEmpty();
    }

    private static JdbcDataSource database() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:bill-snapshot-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        apply(dataSource, "V95__m3_customer_delivery_bill.sql");
        apply(dataSource, "V133__m3_customer_bill_snapshot.sql");
        apply(dataSource, "V136__m3_customer_bill_sources.sql");
        return dataSource;
    }

    private static void apply(JdbcDataSource dataSource, String file) throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/mysql").resolve(file));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String item : MigrationCatalog.statements(sql)) {
                statement.execute(item);
            }
        }
    }

    private static WorkSession session(Connection connection) {
        return new WorkSession() {
            @Override
            public Optional<Connection> connection() {
                return Optional.of(connection);
            }

            @Override
            public void onRollback(Runnable undo) {
            }
        };
    }
}
