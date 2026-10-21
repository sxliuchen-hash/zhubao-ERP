package com.zhubao.erp.approval.infra;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.LossApprovalResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcApprovalTaskRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcApprovalTaskRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema();
        repository = new JdbcApprovalTaskRepository(dataSource);
    }

    @Test
    void persistsAndReloadsApprovalTask() {
        ApprovalTask task = waitingTask();

        repository.save(task);
        JdbcApprovalTaskRepository reloaded = new JdbcApprovalTaskRepository(dataSource);

        assertThat(reloaded.findByCode("LOSS-JDBC-001"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
                    assertThat(saved.sourceSummary()).contains("lossWeight=4.600");
                    assertThat(saved.locked()).isFalse();
                    assertThat(saved.formalPayroll()).isFalse();
                });
        assertThat(reloaded.findAll())
                .extracting(ApprovalTask::approvalCode)
                .containsExactly("LOSS-JDBC-001");
        assertThat(auditEventCount()).isEqualTo(1);
    }

    @Test
    void updatesDecisionAndKeepsAuditHistory() {
        ApprovalService service = new ApprovalService(repository);
        service.createLossApprovalTask(lossCommand(), lossResult(true));

        ApprovalActor leader = new ApprovalActor("leader-a", "leader", RoleCode.WORKSHOP_LEADER);
        ApprovalTask decided = service.decide(
                "LOSS-JDBC-001",
                new ApprovalDecisionCommand("APPROVE", "伪造审批人", "Evidence checked in JDBC test.", "ATT-JDBC-001"),
                leader
        );
        JdbcApprovalTaskRepository reloaded = new JdbcApprovalTaskRepository(dataSource);

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(reloaded.findByCode("LOSS-JDBC-001"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.status()).isEqualTo(ApprovalStatus.APPROVED);
                    assertThat(saved.locked()).isTrue();
                    assertThat(saved.decisionOperator()).isEqualTo("leader-a");
                    assertThat(saved.evidenceCode()).isEqualTo("ATT-JDBC-001");
                });
        assertThat(service.pendingTasks(leader)).isEmpty();
        assertThat(auditEventCount()).isEqualTo(2);
        assertThatThrownBy(() -> service.decide(
                "LOSS-JDBC-001",
                new ApprovalDecisionCommand("REJECT", "leader-a", "重复", null),
                leader
        )).isInstanceOf(BusinessException.class).hasMessageContaining("不能重复决策");
        assertThat(auditEventCount()).isEqualTo(2);
    }

    private ApprovalTask waitingTask() {
        return new ApprovalTask(
                "LOSS-JDBC-001",
                "LOSS_APPROVAL",
                "ZD-JDBC-001",
                "Loss approval for ZD-JDBC-001 / polish",
                ApprovalStatus.WAITING_APPROVAL,
                "operator-a",
                "leader-a",
                "WORKSHOP_LEADER",
                "red",
                "WAITING_APPROVAL",
                "lossWeight=4.600; excessLossWeight=2.177; formalPayroll=false",
                "HO-JDBC-001",
                false,
                "2026-07-07T03:00:00+08:00",
                null,
                null,
                null,
                false
        );
    }

    private ApproveLossCommand lossCommand() {
        return new ApproveLossCommand(
                "LOSS-JDBC-001",
                "MD-JDBC-001",
                "ZD-JDBC-001",
                1001L,
                null,
                "MAT-JDBC",
                "polish",
                "operator-a",
                "leader-a",
                bd("121.150"),
                bd("116.550"),
                bd("0.000"),
                bd("2.0"),
                bd("56"),
                bd("0.80"),
                bd("6.00"),
                "HO-JDBC-001",
                "jdbc test"
        );
    }

    private LossApprovalResult lossResult(boolean overLimit) {
        return new LossApprovalResult(
                "LOSS-JDBC-001",
                "MD-JDBC-001",
                "ZD-JDBC-001",
                "polish",
                bd("121.150"),
                bd("116.550"),
                bd("0.000"),
                overLimit ? bd("4.600") : bd("1.000"),
                bd("2.423"),
                overLimit ? bd("2.177") : bd("0.000"),
                bd("2.00"),
                overLimit ? bd("3.80") : bd("0.82"),
                overLimit,
                overLimit ? "WAITING_APPROVAL" : "AUTO_APPROVED",
                bd("56"),
                bd("0.80"),
                bd("44.80"),
                overLimit ? bd("13.06") : bd("0.00"),
                overLimit ? bd("31.74") : bd("44.80"),
                false,
                "operator-a",
                "leader-a",
                "HO-JDBC-001"
        );
    }

    private void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table approval_task (
                        id bigint primary key auto_increment,
                        approval_code varchar(64) not null,
                        scene varchar(64) not null,
                        business_code varchar(64) not null,
                        title varchar(255) not null,
                        status varchar(32) not null,
                        applicant varchar(64) not null,
                        approver varchar(64) not null,
                        owner_role varchar(64) not null,
                        severity varchar(16) not null,
                        source_status varchar(64) not null,
                        source_summary text not null,
                        evidence_code varchar(128),
                        formal_payroll boolean not null default false,
                        created_at varchar(64) not null,
                        decided_at varchar(64),
                        decision_operator varchar(64),
                        decision_comment varchar(512),
                        locked boolean not null default false,
                        unique key uk_approval_task_code (approval_code)
                    )
                    """);
            statement.execute("""
                    create table approval_audit_event (
                        id bigint primary key auto_increment,
                        approval_code varchar(64) not null,
                        status varchar(32) not null,
                        operator varchar(64) not null,
                        comment varchar(512),
                        evidence_code varchar(128),
                        occurred_at varchar(64) not null,
                        locked boolean not null default false
                    )
                    """);
        }
    }

    private int auditEventCount() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select count(*) from approval_audit_event")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to count approval audit events", ex);
        }
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
