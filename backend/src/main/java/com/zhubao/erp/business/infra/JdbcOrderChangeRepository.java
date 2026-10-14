package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.business.domain.OrderChangeStatus;
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
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcOrderChangeRepository implements OrderChangeRepository {

    private final DataSource dataSource;

    public JdbcOrderChangeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String INSERT_SQL = """
            insert into biz_order_change (
                id, mother_order_code, work_order_code, old_requirement, new_requirement, old_due_date, new_due_date,
                status_code, version_no, amount_unchanged, customer_unit_price, worker_fee, finance_note, confirmed_by,
                request_id, created_by, created_at, updated_at,
                customer_delta, worker_delta, receivable_code, process_name, worker_name,
                old_route, new_route, expected_version, rework_process
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Override
    public OrderChange save(OrderChange change) {
        try (Connection connection = dataSource.getConnection()) {
            return saveOn(connection, change);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存改单", ex);
        }
    }

    @Override
    public OrderChange save(OrderChange change, WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("改单写入必须使用工作单元连接");
        }
        try {
            return saveOn(session.connection().get(), change);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存改单", ex);
        }
    }

    /**
     * 同一请求编号只保留先入库的那一行。后来的编号不同就返回库里的真实记录，不改它的状态。
     * 同一编号再次保存才更新状态，供财务确认和生效使用。
     */
    private OrderChange saveOn(Connection connection, OrderChange change) throws SQLException {
        Optional<OrderChange> existing = readByRequest(connection, change.requestId());
        if (existing.isPresent() && !existing.get().id().equals(change.id())) {
            return existing.get();
        }
        if (existing.isPresent()) {
            update(connection, change);
            return readByRequest(connection, change.requestId()).orElse(change);
        }
        try {
            write(connection, INSERT_SQL, change);
        } catch (SQLException ex) {
            if (!duplicate(ex)) {
                throw ex;
            }
            return readByRequest(connection, change.requestId()).orElseThrow(() -> ex);
        }
        return readByRequest(connection, change.requestId()).orElse(change);
    }

    private void update(Connection connection, OrderChange change) throws SQLException {
        String sql = """
                update biz_order_change
                   set status_code = ?, amount_unchanged = ?, customer_unit_price = ?, worker_fee = ?,
                       finance_note = ?, confirmed_by = ?, updated_at = ?, customer_delta = ?, worker_delta = ?,
                       receivable_code = ?, process_name = ?, worker_name = ?, old_route = ?, new_route = ?,
                       expected_version = ?, rework_process = ?
                 where id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, change.status().name());
            statement.setBoolean(2, change.amountUnchanged());
            statement.setBigDecimal(3, change.customerUnitPrice());
            statement.setBigDecimal(4, change.workerFee());
            statement.setString(5, change.financeNote());
            statement.setString(6, change.confirmedBy());
            statement.setString(7, change.updatedAt());
            statement.setBigDecimal(8, change.customerDelta());
            statement.setBigDecimal(9, change.workerDelta());
            statement.setString(10, change.receivableCode());
            statement.setString(11, change.processName());
            statement.setString(12, change.workerName());
            statement.setString(13, change.oldRoute());
            statement.setString(14, change.newRoute());
            statement.setInt(15, change.expectedVersion());
            statement.setString(16, change.reworkProcess());
            statement.setString(17, change.id());
            statement.executeUpdate();
        }
    }

    private static boolean duplicate(SQLException ex) {
        SQLException current = ex;
        while (current != null) {
            String state = current.getSQLState() == null ? "" : current.getSQLState();
            if (current.getErrorCode() == 1062 || current.getErrorCode() == 23505 || state.startsWith("23")) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private void write(Connection connection, String sql, OrderChange change) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, change.id());
            statement.setString(2, change.motherOrderCode());
            statement.setString(3, change.workOrderCode());
            statement.setString(4, change.oldRequirement());
            statement.setString(5, change.newRequirement());
            statement.setString(6, change.oldDueDate());
            statement.setString(7, change.newDueDate());
            statement.setString(8, change.status().name());
            statement.setInt(9, change.versionNo());
            statement.setBoolean(10, change.amountUnchanged());
            statement.setBigDecimal(11, change.customerUnitPrice());
            statement.setBigDecimal(12, change.workerFee());
            statement.setString(13, change.financeNote());
            statement.setString(14, change.confirmedBy());
            statement.setString(15, change.requestId());
            statement.setString(16, change.createdBy());
            statement.setString(17, change.createdAt());
            statement.setString(18, change.updatedAt());
            statement.setBigDecimal(19, change.customerDelta());
            statement.setBigDecimal(20, change.workerDelta());
            statement.setString(21, change.receivableCode());
            statement.setString(22, change.processName());
            statement.setString(23, change.workerName());
            statement.setString(24, change.oldRoute());
            statement.setString(25, change.newRoute());
            statement.setInt(26, change.expectedVersion());
            statement.setString(27, change.reworkProcess());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<OrderChange> findById(String id) {
        return queryOne("select * from biz_order_change where id = ?", id);
    }

    @Override
    public Optional<OrderChange> findByRequestId(String requestId) {
        return queryOne("select * from biz_order_change where request_id = ?", requestId);
    }

    @Override
    public int latestAppliedVersion(String businessCode) {
        String sql = """
                select max(version_no) from biz_order_change
                 where status_code = 'APPLIED' and (mother_order_code = ? or work_order_code = ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, businessCode);
            statement.setString(2, businessCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                int version = rs.getInt(1);
                return rs.wasNull() ? 0 : version;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取改单版本", ex);
        }
    }

    @Override
    public List<OrderChange> listByMother(String motherOrderCode) {
        String sql = "select * from biz_order_change where mother_order_code = ? order by version_no";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, motherOrderCode);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrderChange> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出改单", ex);
        }
    }

    private Optional<OrderChange> readByRequest(Connection connection, String requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select * from biz_order_change where request_id = ?")) {
            statement.setString(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    private Optional<OrderChange> queryOne(String sql, String parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取改单", ex);
        }
    }

    private OrderChange map(ResultSet rs) throws SQLException {
        return new OrderChange(
                rs.getString("id"),
                rs.getString("mother_order_code"),
                rs.getString("work_order_code"),
                rs.getString("old_requirement"),
                rs.getString("new_requirement"),
                rs.getString("old_due_date"),
                rs.getString("new_due_date"),
                OrderChangeStatus.valueOf(rs.getString("status_code")),
                rs.getInt("version_no"),
                rs.getBoolean("amount_unchanged"),
                rs.getBigDecimal("customer_unit_price"),
                rs.getBigDecimal("worker_fee"),
                rs.getString("finance_note"),
                rs.getString("confirmed_by"),
                rs.getString("request_id"),
                rs.getString("created_by"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                rs.getBigDecimal("customer_delta"),
                rs.getBigDecimal("worker_delta"),
                rs.getString("receivable_code"),
                rs.getString("process_name"),
                rs.getString("worker_name"),
                rs.getString("old_route"),
                rs.getString("new_route"),
                rs.getInt("expected_version"),
                rs.getString("rework_process")
        );
    }
}
