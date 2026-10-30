package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.CustomerReturnRepository;
import com.zhubao.erp.business.domain.CustomerReturnSource;
import com.zhubao.erp.business.domain.CustomerReturnStatus;
import com.zhubao.erp.common.error.BusinessException;
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
@ConditionalOnProperty(name = "zhubao.customer-return.repository", havingValue = "jdbc")
public class JdbcCustomerReturnRepository implements CustomerReturnRepository {

    private final DataSource dataSource;

    public JdbcCustomerReturnRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(CustomerReturnSource source, WorkSession session) {
        Connection connection = WorkSessionBindings.requiredJdbc(session);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into biz_customer_return_source(
                    return_id, request_id, delivery_id, outbound_code, bag_id, quantity, weight_value,
                    status_code, adjustment_basis, version_no)
                values (?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, source.returnId());
            statement.setString(2, source.requestId());
            statement.setString(3, source.deliveryId());
            statement.setString(4, source.outboundCode());
            statement.setString(5, source.bagId());
            statement.setBigDecimal(6, source.quantity());
            statement.setBigDecimal(7, source.weight());
            statement.setString(8, source.status().name());
            statement.setString(9, source.adjustmentBasis());
            statement.setInt(10, source.version());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                throw new BusinessException("退货来源已存在");
            }
            throw new IllegalStateException("无法保存退货来源", ex);
        }
    }

    @Override
    public Optional<CustomerReturnSource> find(String returnId) {
        return list("return_id=?", returnId).stream().findFirst();
    }

    @Override
    public Optional<CustomerReturnSource> findByRequest(String requestId) {
        return list("request_id=?", requestId).stream().findFirst();
    }

    @Override
    public List<CustomerReturnSource> listByOutbound(String outboundCode) {
        return list("outbound_code=?", outboundCode);
    }

    private List<CustomerReturnSource> list(String where, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from biz_customer_return_source where " + where + " order by return_id")) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                List<CustomerReturnSource> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取退货来源", ex);
        }
    }

    private CustomerReturnSource map(ResultSet rows) throws SQLException {
        return new CustomerReturnSource(
                rows.getString("return_id"),
                rows.getString("request_id"),
                rows.getString("delivery_id"),
                rows.getString("outbound_code"),
                rows.getString("bag_id"),
                rows.getBigDecimal("quantity"),
                rows.getBigDecimal("weight_value"),
                CustomerReturnStatus.valueOf(rows.getString("status_code")),
                rows.getString("adjustment_basis"),
                rows.getInt("version_no")
        );
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        return ex.getErrorCode() == 1062 || ex.getErrorCode() == 23505
                || "23000".equals(state) || "23505".equals(state);
    }
}
