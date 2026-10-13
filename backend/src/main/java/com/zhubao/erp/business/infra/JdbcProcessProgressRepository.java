package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ProcessProgressEvent;
import com.zhubao.erp.business.domain.ProcessProgressHead;
import com.zhubao.erp.business.domain.ProcessProgressRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "jdbc")
public class JdbcProcessProgressRepository implements ProcessProgressRepository {

    private final DataSource dataSource;

    public JdbcProcessProgressRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ProcessProgressHead> find(String orderCode, String processName) {
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, orderCode, processName, false);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取工序进度", ex);
        }
    }

    @Override
    public Optional<ProcessProgressHead> find(String orderCode, String processName, WorkSession session) {
        try {
            return find(requireConnection(session), orderCode, processName, true);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取工序进度", ex);
        }
    }

    @Override
    public List<ProcessProgressHead> listAll() {
        String sql = """
                select order_code, process_name, percent_value, version_no, note_text, operator_name, updated_at
                  from biz_process_progress
                 order by order_code, process_name
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<ProcessProgressHead> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapHead(rs));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法列出工序进度", ex);
        }
    }

    @Override
    public void save(ProcessProgressHead head, WorkSession session) {
        String sql = """
                insert into biz_process_progress (
                    order_code, process_name, percent_value, version_no, note_text, operator_name, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    percent_value = values(percent_value),
                    version_no = values(version_no),
                    note_text = values(note_text),
                    operator_name = values(operator_name),
                    updated_at = values(updated_at)
                """;
        try (PreparedStatement statement = requireConnection(session).prepareStatement(sql)) {
            statement.setString(1, head.orderCode());
            statement.setString(2, head.processName());
            statement.setInt(3, head.percent());
            statement.setInt(4, head.version());
            statement.setString(5, head.note());
            statement.setString(6, head.operator());
            statement.setString(7, head.updatedAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存工序进度", ex);
        }
    }

    @Override
    public ProcessProgressEvent append(ProcessProgressEvent event, WorkSession session) {
        String sql = """
                insert into biz_process_progress_event (
                    order_code, process_name, old_percent, new_percent, note_text, reason_text, operator_name, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = requireConnection(session).prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, event.orderCode());
            statement.setString(2, event.processName());
            statement.setInt(3, event.oldPercent());
            statement.setInt(4, event.newPercent());
            statement.setString(5, event.note());
            statement.setString(6, event.reason());
            statement.setString(7, event.operator());
            statement.setString(8, event.createdAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0L;
                return new ProcessProgressEvent(
                        id, event.orderCode(), event.processName(), event.oldPercent(), event.newPercent(),
                        event.note(), event.reason(), event.operator(), event.createdAt()
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法保存进度历史", ex);
        }
    }

    @Override
    public List<ProcessProgressEvent> history(String orderCode, String processName) {
        String sql = """
                select id, order_code, process_name, old_percent, new_percent, note_text, reason_text, operator_name, created_at
                  from biz_process_progress_event
                 where order_code = ? and process_name = ?
                 order by id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderCode);
            statement.setString(2, processName);
            try (ResultSet rs = statement.executeQuery()) {
                List<ProcessProgressEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new ProcessProgressEvent(
                            rs.getLong("id"),
                            rs.getString("order_code"),
                            rs.getString("process_name"),
                            rs.getInt("old_percent"),
                            rs.getInt("new_percent"),
                            rs.getString("note_text"),
                            rs.getString("reason_text"),
                            rs.getString("operator_name"),
                            rs.getString("created_at")
                    ));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取进度历史", ex);
        }
    }

    private Optional<ProcessProgressHead> find(Connection connection, String orderCode, String processName, boolean lock) throws SQLException {
        String sql = """
                select order_code, process_name, percent_value, version_no, note_text, operator_name, updated_at
                  from biz_process_progress
                 where order_code = ? and process_name = ?
                """ + (lock ? " for update" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderCode);
            statement.setString(2, processName);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapHead(rs));
            }
        }
    }

    private ProcessProgressHead mapHead(ResultSet rs) throws SQLException {
        return new ProcessProgressHead(
                rs.getString("order_code"),
                rs.getString("process_name"),
                rs.getInt("percent_value"),
                rs.getInt("version_no"),
                rs.getString("note_text"),
                rs.getString("operator_name"),
                rs.getString("updated_at")
        );
    }

    private Connection requireConnection(WorkSession session) {
        if (session == null || session.connection().isEmpty()) {
            throw new IllegalStateException("进度写入必须使用工作单元连接");
        }
        return session.connection().get();
    }
}
