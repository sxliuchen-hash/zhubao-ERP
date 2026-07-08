package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.audit.repository", havingValue = "jdbc")
public class JdbcAuthAuditRepository implements AuthAuditRepository {

    private final DataSource dataSource;

    public JdbcAuthAuditRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public AuthAuditEvent save(AuthAuditEvent event) {
        String sql = """
                insert into auth_audit_event (
                    event_id, event_type, username, operator, role, result, reason, occurred_at, session_token_suffix
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.eventId());
            statement.setString(2, event.eventType());
            statement.setString(3, event.username());
            statement.setString(4, event.operator());
            statement.setString(5, event.role());
            statement.setString(6, event.result());
            statement.setString(7, event.reason());
            statement.setString(8, event.occurredAt());
            statement.setString(9, event.sessionTokenSuffix());
            statement.executeUpdate();
            return event;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save auth audit event", ex);
        }
    }

    @Override
    public List<AuthAuditEvent> findRecent(int limit) {
        String sql = """
                select event_id, event_type, username, operator, role, result, reason, occurred_at, session_token_suffix
                  from auth_audit_event
                 order by id desc
                 limit ?
                """;
        List<AuthAuditEvent> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 50)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AuthAuditEvent(
                            rs.getString("event_id"),
                            rs.getString("event_type"),
                            rs.getString("username"),
                            rs.getString("operator"),
                            rs.getString("role"),
                            rs.getString("result"),
                            rs.getString("reason"),
                            rs.getString("occurred_at"),
                            rs.getString("session_token_suffix")
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load auth audit events", ex);
        }
        return rows;
    }
}
