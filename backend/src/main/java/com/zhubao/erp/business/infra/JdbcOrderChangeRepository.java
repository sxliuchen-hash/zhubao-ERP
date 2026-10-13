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

    @Override
    public OrderChange save(OrderChange change) {
        String sql = """
                insert into biz_order_change (
                    id, mother_order_code, work_order_code, old_requirement, new_requirement, old_due_date, new_due_date,
                    status_code, version_no, amount_unchanged, customer_unit_price, worker_fee, finance_note, confirmed_by,
                    request_id, created_by, created_at, updated_at,
                    customer_delta, worker_delta, receivable_code, process_name, worker_name,
                    old_route, new_route, expected_version, rework_process
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    status_code = values(status_code),
                    amount_unchanged = values(amount_unchanged),
                    customer_unit_price = values(customer_unit_price),
                    worker_fee = values(worker_fee),
                    finance_note = values(finance_note),
                    confirmed_by = values(confirmed_by),
                    updated_at = values(updated_at),
                    customer_delta = values(customer_delta),
                    worker_delta = values(worker_delta),
                    receivable_code = values(receivable_code),
                    process_name = values(process_name),
                    worker_name = values(worker_name),
                    old_route = values(old_route),
                    new_route = values(new_route),
                    expected_version = values(expected_version),
                    rework_process = values(rework_process)
                """;
        try (Connection connection = dataSource.getConnection()) {
            write(connection, sql, change);
            return change;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存改单", ex);
        }
    }

    @Override
    public OrderChange save(OrderChange change, WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("改单写入必须使用工作单元连接");
        }
        String sql = """
                insert into biz_order_change (
                    id, mother_order_code, work_order_code, old_requirement, new_requirement, old_due_date, new_due_date,
                    status_code, version_no, amount_unchanged, customer_unit_price, worker_fee, finance_note, confirmed_by,
                    request_id, created_by, created_at, updated_at,
                    customer_delta, worker_delta, receivable_code, process_name, worker_name,
                    old_route, new_route, expected_version, rework_process
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    status_code = values(status_code),
                    amount_unchanged = values(amount_unchanged),
                    customer_unit_price = values(customer_unit_price),
                    worker_fee = values(worker_fee),
                    finance_note = values(finance_note),
                    confirmed_by = values(confirmed_by),
                    updated_at = values(updated_at),
                    customer_delta = values(customer_delta),
                    worker_delta = values(worker_delta),
                    receivable_code = values(receivable_code),
                    process_name = values(process_name),
                    worker_name = values(worker_name),
                    old_route = values(old_route),
                    new_route = values(new_route),
                    expected_version = values(expected_version),
                    rework_process = values(rework_process)
                """;
        try {
            write(session.connection().get(), sql, change);
            return change;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存改单", ex);
        }
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
