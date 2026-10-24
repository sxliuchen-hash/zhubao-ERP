package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.DeskTask;
import com.zhubao.erp.business.domain.DeskTaskKind;
import com.zhubao.erp.business.domain.DeskTaskRepository;
import com.zhubao.erp.business.domain.DeskTaskStatus;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcDeskTaskRepository implements DeskTaskRepository {

    private final DataSource dataSource;

    public JdbcDeskTaskRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DeskTask insert(DeskTask task) {
        String sql = """
                insert into biz_desk_task (
                    task_code, kind, title, note, customer_id, customer_name, mother_order_code,
                    assignee_username, assignee_name, due_date, task_status, creator_username, created_at,
                    request_id, version, completed_at, completed_by, result_note, complete_request_id,
                    cancelled_at, cancelled_by, cancel_reason, cancel_request_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, task);
            statement.executeUpdate();
            return task;
        } catch (SQLException ex) {
            throw insertFailure(ex);
        }
    }

    @Override
    public Optional<DeskTask> updateIfVersion(DeskTask task, int expectedVersion) {
        String sql = """
                update biz_desk_task
                   set task_status = ?, version = ?, completed_at = ?, completed_by = ?, result_note = ?,
                       complete_request_id = ?, cancelled_at = ?, cancelled_by = ?, cancel_reason = ?,
                       cancel_request_id = ?
                 where task_code = ? and version = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, task.status().name());
            statement.setInt(2, task.version());
            statement.setString(3, task.completedAt());
            statement.setString(4, task.completedBy());
            statement.setString(5, task.resultNote());
            nullable(statement, 6, task.completeRequestId());
            statement.setString(7, task.cancelledAt());
            statement.setString(8, task.cancelledBy());
            statement.setString(9, task.cancelReason());
            nullable(statement, 10, task.cancelRequestId());
            statement.setString(11, task.taskCode());
            statement.setInt(12, expectedVersion);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
            return Optional.of(task);
        } catch (SQLException ex) {
            if (isDuplicate(ex)) {
                throw duplicate(ex, true);
            }
            throw new IllegalStateException("待办更新失败", ex);
        }
    }

    @Override
    public Optional<DeskTask> findByCode(String taskCode) {
        return one("where task_code = ?", taskCode);
    }

    @Override
    public Optional<DeskTask> findByRequestId(String requestId) {
        return one("where request_id = ?", requestId);
    }

    @Override
    public Optional<DeskTask> findByCompleteRequestId(String requestId) {
        return one("where complete_request_id = ?", requestId);
    }

    @Override
    public Optional<DeskTask> findByCancelRequestId(String requestId) {
        return one("where cancel_request_id = ?", requestId);
    }

    @Override
    public List<DeskTask> listAll() {
        String sql = select() + " order by due_date, task_code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<DeskTask> tasks = new ArrayList<>();
            while (rows.next()) {
                tasks.add(map(rows));
            }
            return tasks;
        } catch (SQLException ex) {
            throw new IllegalStateException("待办列表读取失败", ex);
        }
    }

    private Optional<DeskTask> one(String where, String value) {
        String sql = select() + " " + where;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value == null ? "" : value.trim());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rows));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("待办读取失败", ex);
        }
    }

    private static String select() {
        return """
                select task_code, kind, title, note, customer_id, customer_name, mother_order_code,
                       assignee_username, assignee_name, due_date, task_status, creator_username, created_at,
                       request_id, version, completed_at, completed_by, result_note, complete_request_id,
                       cancelled_at, cancelled_by, cancel_reason, cancel_request_id
                  from biz_desk_task
                """;
    }

    private static void bind(PreparedStatement statement, DeskTask task) throws SQLException {
        statement.setString(1, task.taskCode());
        statement.setString(2, task.kind().name());
        statement.setString(3, task.title());
        statement.setString(4, task.note());
        if (task.customerId() == null) {
            statement.setNull(5, Types.BIGINT);
        } else {
            statement.setLong(5, task.customerId());
        }
        statement.setString(6, task.customerName());
        statement.setString(7, task.motherOrderCode());
        statement.setString(8, task.assigneeUsername());
        statement.setString(9, task.assigneeName());
        statement.setString(10, task.dueDate());
        statement.setString(11, task.status().name());
        statement.setString(12, task.creatorUsername());
        statement.setString(13, task.createdAt());
        statement.setString(14, task.requestId());
        statement.setInt(15, task.version());
        statement.setString(16, task.completedAt());
        statement.setString(17, task.completedBy());
        statement.setString(18, task.resultNote());
        nullable(statement, 19, task.completeRequestId());
        statement.setString(20, task.cancelledAt());
        statement.setString(21, task.cancelledBy());
        statement.setString(22, task.cancelReason());
        nullable(statement, 23, task.cancelRequestId());
    }

    private static void nullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static DeskTask map(ResultSet rows) throws SQLException {
        long customerId = rows.getLong("customer_id");
        Long customer = rows.wasNull() ? null : customerId;
        return new DeskTask(
                rows.getString("task_code"),
                DeskTaskKind.parse(rows.getString("kind")),
                rows.getString("title"),
                rows.getString("note"),
                customer,
                rows.getString("customer_name"),
                rows.getString("mother_order_code"),
                rows.getString("assignee_username"),
                rows.getString("assignee_name"),
                rows.getString("due_date"),
                DeskTaskStatus.parse(rows.getString("task_status")),
                rows.getString("creator_username"),
                rows.getString("created_at"),
                rows.getString("request_id"),
                rows.getInt("version"),
                rows.getString("completed_at"),
                rows.getString("completed_by"),
                rows.getString("result_note"),
                rows.getString("complete_request_id"),
                rows.getString("cancelled_at"),
                rows.getString("cancelled_by"),
                rows.getString("cancel_reason"),
                rows.getString("cancel_request_id")
        );
    }

    private RuntimeException insertFailure(SQLException ex) {
        if (isDuplicate(ex)) {
            return duplicate(ex, false);
        }
        return new IllegalStateException("待办写入失败", ex);
    }

    /**
     * 只认约束名。更新语句里同时出现完成和作废列，不能靠列名判断撞了哪一个。
     */
    private BusinessException duplicate(SQLException ex, boolean update) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uk_desk_task_cancel_request")) {
            return new BusinessException("作废请求编号已使用");
        }
        if (message.contains("uk_desk_task_complete_request")) {
            return new BusinessException("完成请求编号已使用");
        }
        if (message.contains("uk_desk_task_request")) {
            return new BusinessException("请求编号已使用");
        }
        if (!update && (message.contains("primary") || message.contains("task_code"))) {
            return new BusinessException("待办单号已存在");
        }
        return new BusinessException(update ? "完成请求编号已使用" : "请求编号已使用");
    }

    private boolean isDuplicate(SQLException ex) {
        if (ex.getErrorCode() == 1062) {
            return true;
        }
        String state = ex.getSQLState();
        return state != null && state.startsWith("23");
    }
}
