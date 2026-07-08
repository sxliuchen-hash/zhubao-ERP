package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.pre-approval.repository", havingValue = "jdbc")
public class JdbcAuthAccountPreApprovalApplicationRepository
        implements AuthAccountPreApprovalApplicationRepository {

    private final DataSource dataSource;

    public JdbcAuthAccountPreApprovalApplicationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public AuthAccountPreApprovalApplication save(AuthAccountPreApprovalApplication application) {
        if (findByCode(application.applicationCode()).isPresent()) {
            return update(application);
        }
        return insert(application);
    }

    @Override
    public Optional<AuthAccountPreApprovalApplication> findByCode(String applicationCode) {
        String sql = selectColumns() + """
                  from auth_account_pre_approval_application
                 where application_code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, applicationCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load auth account pre-approval application", ex);
        }
        return Optional.empty();
    }

    @Override
    public List<AuthAccountPreApprovalApplication> findAll() {
        String sql = selectColumns() + """
                  from auth_account_pre_approval_application
                 order by id desc
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            java.util.ArrayList<AuthAccountPreApprovalApplication> applications = new java.util.ArrayList<>();
            while (rs.next()) {
                applications.add(map(rs));
            }
            return applications;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load auth account pre-approval applications", ex);
        }
    }

    private AuthAccountPreApprovalApplication insert(AuthAccountPreApprovalApplication application) {
        String sql = """
                insert into auth_account_pre_approval_application (
                    application_code, draft_code, readiness_code, action_type, target_username, applicant,
                    applicant_role, target_operator, target_role, target_data_scope, target_customer_scope,
                    target_enabled, demo_account, reason, required_evidence, provided_evidence, missing_evidence,
                    missing_capabilities, approver_roles, blocking_status, application_status,
                    ready_for_blocking_approval, source_summary, next_action, risk_level, tone, submitted_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, application);
            statement.executeUpdate();
            return application;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save auth account pre-approval application", ex);
        }
    }

    private AuthAccountPreApprovalApplication update(AuthAccountPreApprovalApplication application) {
        String sql = """
                update auth_account_pre_approval_application
                   set draft_code = ?,
                       readiness_code = ?,
                       action_type = ?,
                       target_username = ?,
                       applicant = ?,
                       applicant_role = ?,
                       target_operator = ?,
                       target_role = ?,
                       target_data_scope = ?,
                       target_customer_scope = ?,
                       target_enabled = ?,
                       demo_account = ?,
                       reason = ?,
                       required_evidence = ?,
                       provided_evidence = ?,
                       missing_evidence = ?,
                       missing_capabilities = ?,
                       approver_roles = ?,
                       blocking_status = ?,
                       application_status = ?,
                       ready_for_blocking_approval = ?,
                       source_summary = ?,
                       next_action = ?,
                       risk_level = ?,
                       tone = ?,
                       submitted_at = ?
                 where application_code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindUpdate(statement, application);
            statement.executeUpdate();
            return application;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update auth account pre-approval application", ex);
        }
    }

    private void bind(PreparedStatement statement, AuthAccountPreApprovalApplication application) throws SQLException {
        statement.setString(1, application.applicationCode());
        statement.setString(2, application.draftCode());
        statement.setString(3, application.readinessCode());
        statement.setString(4, application.actionType());
        statement.setString(5, application.targetUsername());
        statement.setString(6, application.applicant());
        statement.setString(7, application.applicantRole());
        statement.setString(8, application.targetOperator());
        statement.setString(9, application.targetRole());
        statement.setString(10, application.targetDataScope());
        statement.setString(11, serialize(application.targetCustomerScope()));
        setBoolean(statement, 12, application.targetEnabled());
        setBoolean(statement, 13, application.demoAccount());
        statement.setString(14, application.reason());
        statement.setString(15, serialize(application.requiredEvidence()));
        statement.setString(16, serialize(application.providedEvidence()));
        statement.setString(17, serialize(application.missingEvidence()));
        statement.setString(18, serialize(application.missingCapabilities()));
        statement.setString(19, serialize(application.approverRoles()));
        statement.setString(20, application.blockingStatus());
        statement.setString(21, application.applicationStatus());
        statement.setBoolean(22, application.readyForBlockingApproval());
        statement.setString(23, application.sourceSummary());
        statement.setString(24, application.nextAction());
        statement.setString(25, application.riskLevel());
        statement.setString(26, application.tone());
        statement.setString(27, application.submittedAt());
    }

    private void bindUpdate(PreparedStatement statement, AuthAccountPreApprovalApplication application) throws SQLException {
        statement.setString(1, application.draftCode());
        statement.setString(2, application.readinessCode());
        statement.setString(3, application.actionType());
        statement.setString(4, application.targetUsername());
        statement.setString(5, application.applicant());
        statement.setString(6, application.applicantRole());
        statement.setString(7, application.targetOperator());
        statement.setString(8, application.targetRole());
        statement.setString(9, application.targetDataScope());
        statement.setString(10, serialize(application.targetCustomerScope()));
        setBoolean(statement, 11, application.targetEnabled());
        setBoolean(statement, 12, application.demoAccount());
        statement.setString(13, application.reason());
        statement.setString(14, serialize(application.requiredEvidence()));
        statement.setString(15, serialize(application.providedEvidence()));
        statement.setString(16, serialize(application.missingEvidence()));
        statement.setString(17, serialize(application.missingCapabilities()));
        statement.setString(18, serialize(application.approverRoles()));
        statement.setString(19, application.blockingStatus());
        statement.setString(20, application.applicationStatus());
        statement.setBoolean(21, application.readyForBlockingApproval());
        statement.setString(22, application.sourceSummary());
        statement.setString(23, application.nextAction());
        statement.setString(24, application.riskLevel());
        statement.setString(25, application.tone());
        statement.setString(26, application.submittedAt());
        statement.setString(27, application.applicationCode());
    }

    private AuthAccountPreApprovalApplication map(ResultSet rs) throws SQLException {
        return new AuthAccountPreApprovalApplication(
                rs.getString("application_code"),
                rs.getString("draft_code"),
                rs.getString("readiness_code"),
                rs.getString("action_type"),
                rs.getString("target_username"),
                rs.getString("applicant"),
                rs.getString("applicant_role"),
                rs.getString("target_operator"),
                rs.getString("target_role"),
                rs.getString("target_data_scope"),
                parse(rs.getString("target_customer_scope")),
                readBoolean(rs, "target_enabled"),
                readBoolean(rs, "demo_account"),
                rs.getString("reason"),
                parse(rs.getString("required_evidence")),
                parse(rs.getString("provided_evidence")),
                parse(rs.getString("missing_evidence")),
                parse(rs.getString("missing_capabilities")),
                parse(rs.getString("approver_roles")),
                rs.getString("blocking_status"),
                rs.getString("application_status"),
                rs.getBoolean("ready_for_blocking_approval"),
                rs.getString("source_summary"),
                rs.getString("next_action"),
                rs.getString("risk_level"),
                rs.getString("tone"),
                rs.getString("submitted_at")
        );
    }

    private String selectColumns() {
        return """
                select application_code, draft_code, readiness_code, action_type, target_username, applicant,
                       applicant_role, target_operator, target_role, target_data_scope, target_customer_scope,
                       target_enabled, demo_account, reason, required_evidence, provided_evidence, missing_evidence,
                       missing_capabilities, approver_roles, blocking_status, application_status,
                       ready_for_blocking_approval, source_summary, next_action, risk_level, tone, submitted_at
                """;
    }

    private void setBoolean(PreparedStatement statement, int index, Boolean value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
            return;
        }
        statement.setBoolean(index, value);
    }

    private Boolean readBoolean(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : (Boolean) value;
    }

    private String serialize(List<String> values) {
        return values == null ? "" : String.join("|", values);
    }

    private List<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[|]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
