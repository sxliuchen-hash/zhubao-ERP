package com.zhubao.erp.approval.infra;

import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.domain.ApprovalTaskRepository;
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
@ConditionalOnProperty(name = "zhubao.approval.repository", havingValue = "jdbc")
public class JdbcApprovalTaskRepository implements ApprovalTaskRepository {

    private final DataSource dataSource;

    public JdbcApprovalTaskRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ApprovalTask save(ApprovalTask task) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (exists(connection, task.approvalCode())) {
                    updateTask(connection, task);
                } else {
                    insertTask(connection, task);
                }
                insertAuditEvent(connection, task);
                connection.commit();
                return task;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save approval task", ex);
        }
    }

    @Override
    public Optional<ApprovalTask> findByCode(String approvalCode) {
        String sql = """
                select approval_code, scene, business_code, title, status, applicant, approver, owner_role,
                       severity, source_status, source_summary, evidence_code, formal_payroll, created_at,
                       decided_at, decision_operator, decision_comment, locked
                  from approval_task
                 where approval_code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, approvalCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTask(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load approval task: " + approvalCode, ex);
        }
        return Optional.empty();
    }

    @Override
    public List<ApprovalTask> findAll() {
        String sql = """
                select approval_code, scene, business_code, title, status, applicant, approver, owner_role,
                       severity, source_status, source_summary, evidence_code, formal_payroll, created_at,
                       decided_at, decision_operator, decision_comment, locked
                  from approval_task
                 order by id
                """;
        List<ApprovalTask> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(mapTask(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load approval tasks", ex);
        }
        return rows;
    }

    private boolean exists(Connection connection, String approvalCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select id from approval_task where approval_code = ?")) {
            statement.setString(1, approvalCode);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertTask(Connection connection, ApprovalTask task) throws SQLException {
        String sql = """
                insert into approval_task (
                    approval_code, scene, business_code, title, status, applicant, approver, owner_role,
                    severity, source_status, source_summary, evidence_code, formal_payroll, created_at,
                    decided_at, decision_operator, decision_comment, locked
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTask(statement, task);
            statement.executeUpdate();
        }
    }

    private void updateTask(Connection connection, ApprovalTask task) throws SQLException {
        String sql = """
                update approval_task
                   set scene = ?, business_code = ?, title = ?, status = ?, applicant = ?, approver = ?,
                       owner_role = ?, severity = ?, source_status = ?, source_summary = ?, evidence_code = ?,
                       formal_payroll = ?, created_at = ?, decided_at = ?, decision_operator = ?,
                       decision_comment = ?, locked = ?
                 where approval_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, task.scene());
            statement.setString(2, task.businessCode());
            statement.setString(3, task.title());
            statement.setString(4, task.status().name());
            statement.setString(5, task.applicant());
            statement.setString(6, task.approver());
            statement.setString(7, task.ownerRole());
            statement.setString(8, task.severity());
            statement.setString(9, task.sourceStatus());
            statement.setString(10, task.sourceSummary());
            statement.setString(11, task.evidenceCode());
            statement.setBoolean(12, task.formalPayroll());
            statement.setString(13, task.createdAt());
            statement.setString(14, task.decidedAt());
            statement.setString(15, task.decisionOperator());
            statement.setString(16, task.decisionComment());
            statement.setBoolean(17, task.locked());
            statement.setString(18, task.approvalCode());
            statement.executeUpdate();
        }
    }

    private void bindTask(PreparedStatement statement, ApprovalTask task) throws SQLException {
        statement.setString(1, task.approvalCode());
        statement.setString(2, task.scene());
        statement.setString(3, task.businessCode());
        statement.setString(4, task.title());
        statement.setString(5, task.status().name());
        statement.setString(6, task.applicant());
        statement.setString(7, task.approver());
        statement.setString(8, task.ownerRole());
        statement.setString(9, task.severity());
        statement.setString(10, task.sourceStatus());
        statement.setString(11, task.sourceSummary());
        statement.setString(12, task.evidenceCode());
        statement.setBoolean(13, task.formalPayroll());
        statement.setString(14, task.createdAt());
        statement.setString(15, task.decidedAt());
        statement.setString(16, task.decisionOperator());
        statement.setString(17, task.decisionComment());
        statement.setBoolean(18, task.locked());
    }

    private void insertAuditEvent(Connection connection, ApprovalTask task) throws SQLException {
        String sql = """
                insert into approval_audit_event (
                    approval_code, status, operator, comment, evidence_code, occurred_at, locked
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, task.approvalCode());
            statement.setString(2, task.status().name());
            statement.setString(3, auditOperator(task));
            statement.setString(4, auditComment(task));
            statement.setString(5, task.evidenceCode());
            statement.setString(6, task.decidedAt() == null ? task.createdAt() : task.decidedAt());
            statement.setBoolean(7, task.locked());
            statement.executeUpdate();
        }
    }

    private String auditOperator(ApprovalTask task) {
        if (task.decisionOperator() != null && !task.decisionOperator().isBlank()) {
            return task.decisionOperator();
        }
        if (task.applicant() != null && !task.applicant().isBlank()) {
            return task.applicant();
        }
        return "SYSTEM";
    }

    private String auditComment(ApprovalTask task) {
        if (task.decisionComment() != null && !task.decisionComment().isBlank()) {
            return task.decisionComment();
        }
        return "Approval task saved with status " + task.status().name();
    }

    private ApprovalTask mapTask(ResultSet rs) throws SQLException {
        return new ApprovalTask(
                rs.getString("approval_code"),
                rs.getString("scene"),
                rs.getString("business_code"),
                rs.getString("title"),
                ApprovalStatus.valueOf(rs.getString("status")),
                rs.getString("applicant"),
                rs.getString("approver"),
                rs.getString("owner_role"),
                rs.getString("severity"),
                rs.getString("source_status"),
                rs.getString("source_summary"),
                rs.getString("evidence_code"),
                rs.getBoolean("formal_payroll"),
                rs.getString("created_at"),
                rs.getString("decided_at"),
                rs.getString("decision_operator"),
                rs.getString("decision_comment"),
                rs.getBoolean("locked")
        );
    }
}
