package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select * from fin_customer_delivery_bill_line where bill_id=? order by line_no
                     """)) {
            statement.setString(1, billId);
            try (ResultSet rows = statement.executeQuery()) {
                List<CustomerDeliveryBillLine> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new CustomerDeliveryBillLine(
                            rows.getString("bill_id"), rows.getInt("line_no"), rows.getString("item_name"),
                            CustomerChargeMethod.valueOf(rows.getString("method_code")),
                            rows.getBigDecimal("amount"), rows.getString("expression_text")));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取客户账单明细", ex);
        }
    }

    private void insertLine(Connection connection, CustomerDeliveryBillLine line) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fin_customer_delivery_bill_line(
                    bill_id, line_no, item_name, method_code, amount, expression_text)
                values (?,?,?,?,?,?)
                """)) {
            statement.setString(1, line.billId());
            statement.setInt(2, line.lineNo());
            statement.setString(3, line.itemName());
            statement.setString(4, line.method().name());
            statement.setBigDecimal(5, line.amount());
            statement.setString(6, line.expression());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存客户账单明细", ex);
        }
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

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
