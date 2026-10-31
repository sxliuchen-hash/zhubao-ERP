package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.CustomerBillCoverage;
import com.zhubao.erp.finance.domain.CustomerChargeSource;
import com.zhubao.erp.finance.domain.CustomerChargeDirection;
import com.zhubao.erp.finance.domain.CustomerChargeMethod;
import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillKind;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillLine;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillRepository;
import com.zhubao.erp.work.app.WorkSessionBindings;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.customer-delivery-bill.repository", havingValue = "jdbc")
public class JdbcCustomerDeliveryBillRepository implements CustomerDeliveryBillRepository {

    private final DataSource dataSource;

    public JdbcCustomerDeliveryBillRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(CustomerDeliveryBill bill, List<CustomerDeliveryBillLine> lines, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_customer_delivery_bill(
                    bill_id, request_id, root_bill_id, delivery_id, customer_id, bill_kind,
                    total_amount, version_no, initial_delivery_id)
                values (?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, bill.billId());
            statement.setString(2, bill.requestId());
            statement.setString(3, bill.rootBillId());
            statement.setString(4, bill.deliveryId());
            statement.setLong(5, bill.customerId());
            statement.setString(6, bill.kind().name());
            statement.setBigDecimal(7, bill.totalAmount());
            statement.setInt(8, bill.version());
            if (bill.kind() == CustomerDeliveryBillKind.INITIAL) {
                statement.setString(9, bill.deliveryId());
            } else {
                statement.setString(9, null);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("同一已确认发货单只能有一张初始客户账单");
            }
            throw new IllegalStateException("无法保存客户账单", ex);
        }
        if (!lines.isEmpty()) {
            insertSources(connection, bill.billId(), lines.get(0).coverageSources());
        }
        for (CustomerDeliveryBillLine line : lines) {
            insertLine(connection, line);
        }
    }

    @Override
    public Optional<CustomerDeliveryBill> find(String billId) {
        return list("bill_id=?", billId).stream().findFirst();
    }

    @Override
    public Optional<CustomerDeliveryBill> findByRequest(String requestId) {
        return list("request_id=?", requestId).stream().findFirst();
    }

    @Override
    public Optional<CustomerDeliveryBill> findInitial(String deliveryId) {
        return list("initial_delivery_id=?", deliveryId).stream().findFirst();
    }

    @Override
    public List<CustomerDeliveryBill> listByRoot(String rootBillId) {
        return list("root_bill_id=? order by version_no", rootBillId);
    }

    @Override
    public List<CustomerDeliveryBillLine> lines(String billId) {
        List<CustomerChargeSource> sources = sources(billId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select * from fin_customer_delivery_bill_line where bill_id=? order by line_no
                     """)) {
            statement.setString(1, billId);
            try (ResultSet rows = statement.executeQuery()) {
                List<CustomerDeliveryBillLine> result = new ArrayList<>();
                while (rows.next()) {
                    String direction = rows.getString("direction_code");
                    result.add(new CustomerDeliveryBillLine(
                            rows.getString("bill_id"), rows.getInt("line_no"), rows.getString("item_name"),
                            CustomerChargeMethod.valueOf(rows.getString("method_code")),
                            rows.getBigDecimal("amount"), rows.getString("expression_text"),
                            text(rows.getString("target_code")), text(rows.getString("quantity_unit")),
                            direction == null || direction.isBlank() ? null : CustomerChargeDirection.valueOf(direction),
                            rows.getBigDecimal("input_quantity"), rows.getBigDecimal("unit_price"),
                            rows.getBigDecimal("base_quantity"), rows.getBigDecimal("exempt_quantity"),
                            rows.getBigDecimal("direct_amount"), rows.getBigDecimal("bill_quantity"),
                            text(rows.getString("basis_text")), rows.getBigDecimal("shipped_quantity"),
                            rows.getBigDecimal("loss_quantity"), rows.getString("coverage_unit"),
                            integer(rows, "quantity_scale"), integer(rows, "price_scale"),
                            integer(rows, "money_scale"), rows.getString("rounding_mode"), sources));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取客户账单明细", ex);
        }
    }

    private void insertSources(Connection connection, String billId, List<CustomerChargeSource> sources) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_customer_bill_source(bill_id, source_no, target_code, quantity_unit, shipped_quantity)
                values (?,?,?,?,?)
                """)) {
            for (int index = 0; index < sources.size(); index++) {
                CustomerChargeSource source = sources.get(index);
                statement.setString(1, billId);
                statement.setInt(2, index + 1);
                statement.setString(3, source.targetCode());
                statement.setString(4, source.quantityUnit());
                statement.setBigDecimal(5, source.quantity());
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存账单交付来源", ex);
        }
    }

    private List<CustomerChargeSource> sources(String billId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select target_code, quantity_unit, shipped_quantity from fin_customer_bill_source where bill_id=? order by source_no")) {
            statement.setString(1, billId);
            try (ResultSet rows = statement.executeQuery()) {
                List<CustomerChargeSource> sources = new ArrayList<>();
                while (rows.next()) {
                    sources.add(new CustomerChargeSource(rows.getString(1), rows.getString(2), rows.getBigDecimal(3)));
                }
                return List.copyOf(sources);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取账单交付来源", ex);
        }
    }

    private void insertLine(Connection connection, CustomerDeliveryBillLine line) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_customer_delivery_bill_line(
                    bill_id, line_no, item_name, method_code, amount, expression_text,
                    target_code, quantity_unit, direction_code, input_quantity, unit_price,
                    base_quantity, exempt_quantity, direct_amount, bill_quantity, basis_text,
                    shipped_quantity, loss_quantity, coverage_unit,
                    quantity_scale, price_scale, money_scale, rounding_mode)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, line.billId());
            statement.setInt(2, line.lineNo());
            statement.setString(3, line.itemName());
            statement.setString(4, line.method().name());
            statement.setBigDecimal(5, line.amount());
            statement.setString(6, line.expression());
            statement.setString(7, line.targetCode());
            statement.setString(8, line.quantityUnit());
            if (line.direction() == null) {
                statement.setNull(9, Types.VARCHAR);
            } else {
                statement.setString(9, line.direction().name());
            }
            decimal(statement, 10, line.inputQuantity());
            decimal(statement, 11, line.unitPrice());
            decimal(statement, 12, line.baseQuantity());
            decimal(statement, 13, line.exemptQuantity());
            decimal(statement, 14, line.directAmount());
            decimal(statement, 15, line.billQuantity());
            statement.setString(16, line.basis());
            decimal(statement, 17, line.shippedQuantity());
            decimal(statement, 18, line.lossQuantity());
            statement.setString(19, line.coverageUnit());
            integer(statement, 20, line.quantityScale());
            integer(statement, 21, line.priceScale());
            integer(statement, 22, line.moneyScale());
            statement.setString(23, line.rounding());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存客户账单明细", ex);
        }
    }

    private static void decimal(PreparedStatement statement, int index, java.math.BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static void integer(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static Integer integer(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private List<CustomerDeliveryBill> list(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from fin_customer_delivery_bill where " + where)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                List<CustomerDeliveryBill> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取客户账单", ex);
        }
    }

    private CustomerDeliveryBill map(ResultSet rows) throws SQLException {
        return new CustomerDeliveryBill(
                rows.getString("bill_id"), rows.getString("request_id"), rows.getString("root_bill_id"),
                rows.getString("delivery_id"), rows.getLong("customer_id"),
                CustomerDeliveryBillKind.valueOf(rows.getString("bill_kind")),
                rows.getBigDecimal("total_amount"), rows.getInt("version_no"));
    }

    @Override
    public void saveCoverage(CustomerBillCoverage coverage, BigDecimal receivableAmount, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try {
            lockCoverage(connection, coverage.receivableCode());
            if (coveredOn(connection, coverage.receivableCode()).add(coverage.amount()).compareTo(receivableAmount) > 0) {
                throw new BusinessException("覆盖金额超过旧凭据剩余额度");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into fin_customer_bill_coverage(bill_id, receivable_code, amount)
                    values (?,?,?)
                    """)) {
                statement.setString(1, coverage.billId());
                statement.setString(2, coverage.receivableCode());
                statement.setBigDecimal(3, coverage.amount());
                statement.executeUpdate();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存旧凭据覆盖", ex);
        }
    }

    @Override
    public BigDecimal coveredTotal(String receivableCode) {
        try (Connection connection = dataSource.getConnection()) {
            return coveredOn(connection, receivableCode);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取旧凭据覆盖", ex);
        }
    }

    @Override
    public List<CustomerBillCoverage> coverages(String billId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select receivable_code, amount from fin_customer_bill_coverage where bill_id=? order by receivable_code")) {
            statement.setString(1, billId);
            try (ResultSet rows = statement.executeQuery()) {
                List<CustomerBillCoverage> result = new ArrayList<>();
                while (rows.next()) result.add(new CustomerBillCoverage(billId, rows.getString(1), rows.getBigDecimal(2)));
                return List.copyOf(result);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取账单旧凭据覆盖", ex);
        }
    }

    private void lockCoverage(Connection connection, String receivableCode) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                insert into fin_customer_bill_coverage_guard (receivable_code) values (?)
                on duplicate key update receivable_code = receivable_code
                """)) {
            insert.setString(1, receivableCode);
            insert.executeUpdate();
        }
        try (PreparedStatement lock = connection.prepareStatement(
                "select receivable_code from fin_customer_bill_coverage_guard where receivable_code=? for update")) {
            lock.setString(1, receivableCode);
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("无法锁定旧凭据");
                }
            }
        }
    }

    private BigDecimal coveredOn(Connection connection, String receivableCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select coalesce(sum(amount), 0) from fin_customer_bill_coverage where receivable_code=?")) {
            statement.setString(1, receivableCode);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBigDecimal(1);
            }
        }
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
