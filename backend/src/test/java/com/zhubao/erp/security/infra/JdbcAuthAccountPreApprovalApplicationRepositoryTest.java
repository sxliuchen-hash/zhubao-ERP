package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuthAccountPreApprovalApplicationRepositoryTest {

    private JdbcAuthAccountPreApprovalApplicationRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        repository = new JdbcAuthAccountPreApprovalApplicationRepository(dataSource);
    }

    @Test
    void savesAndReloadsPreApprovalApplication() {
        repository.save(application(
                "AUTH-PRE-APP-202607090001-0001",
                "planner",
                "WAITING_EVIDENCE",
                List.of("直属负责人审批", "系统管理员复核"),
                List.of("审批通过态强制校验", "正式电子签字"),
                false
        ));

        assertThat(repository.findByCode("AUTH-PRE-APP-202607090001-0001"))
                .get()
                .satisfies(application -> {
                    assertThat(application.draftCode()).isEqualTo("AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER");
                    assertThat(application.readinessCode()).isEqualTo("AUTH-PRE-CREATE-001");
                    assertThat(application.actionType()).isEqualTo("创建生产账号");
                    assertThat(application.targetUsername()).isEqualTo("planner");
                    assertThat(application.targetCustomerScope()).containsExactly("恒信珠宝", "雅辰饰品");
                    assertThat(application.targetEnabled()).isTrue();
                    assertThat(application.targetAllowedProcesses()).containsExactly("抛光", "雕刻");
                    assertThat(application.statusAction()).isEqualTo("DISABLE");
                    assertThat(application.demoAccount()).isFalse();
                    assertThat(application.requiredEvidence()).contains("账号申请单", "直属负责人审批");
                    assertThat(application.providedEvidence()).containsExactly("账号申请单：AUTH-REQ-001");
                    assertThat(application.missingEvidence()).containsExactly("直属负责人审批", "系统管理员复核");
                    assertThat(application.missingCapabilities()).contains("审批通过态强制校验");
                    assertThat(application.approverRoles()).containsExactly("GENERAL_MANAGER", "ADMIN");
                    assertThat(application.approvalTaskCode()).isEqualTo("AUTH-PRE-TASK-202607090001-0001");
                    assertThat(application.readyForBlockingApproval()).isFalse();
                    assertThat(application.sourceSummary()).contains("stored pre-approval application");
                    assertThat(application.submittedAt()).isEqualTo("2026-07-09 09:00:00");
                });
    }

    @Test
    void listsNewestApplicationsFirstAndUpdatesByCode() {
        repository.save(application(
                "AUTH-PRE-APP-202607090001-0001",
                "planner",
                "WAITING_EVIDENCE",
                List.of("直属负责人审批"),
                List.of("审批通过态强制校验"),
                false
        ));
        repository.save(application(
                "AUTH-PRE-APP-202607090001-0002",
                "cashier",
                "WAITING_SYSTEM_CAPABILITY",
                List.of(),
                List.of("正式电子签字"),
                false
        ));
        repository.save(application(
                "AUTH-PRE-APP-202607090001-0001",
                "planner",
                "READY_FOR_BLOCKING_APPROVAL",
                List.of(),
                List.of(),
                true
        ));

        assertThat(repository.findAll())
                .extracting(AuthAccountPreApprovalApplication::applicationCode)
                .containsExactly(
                        "AUTH-PRE-APP-202607090001-0002",
                        "AUTH-PRE-APP-202607090001-0001"
                );
        assertThat(repository.findByCode("AUTH-PRE-APP-202607090001-0001"))
                .get()
                .satisfies(application -> {
                    assertThat(application.applicationStatus()).isEqualTo("READY_FOR_BLOCKING_APPROVAL");
                    assertThat(application.approvalTaskCode()).isEqualTo("AUTH-PRE-TASK-202607090001-0001");
                    assertThat(application.missingEvidence()).isEmpty();
                    assertThat(application.missingCapabilities()).isEmpty();
                    assertThat(application.readyForBlockingApproval()).isTrue();
                });
    }

    private AuthAccountPreApprovalApplication application(
            String applicationCode,
            String targetUsername,
            String applicationStatus,
            List<String> missingEvidence,
            List<String> missingCapabilities,
            boolean readyForBlockingApproval
    ) {
        return new AuthAccountPreApprovalApplication(
                applicationCode,
                "AUTH-PRE-DRAFT-CREATE_ACCOUNT-" + targetUsername.toUpperCase(),
                "AUTH-PRE-CREATE-001",
                "创建生产账号",
                targetUsername,
                "系统管理员",
                "ADMIN",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝", "雅辰饰品"),
                true,
                false,
                "新增业务账号前置审批申请",
                List.of("账号申请单", "直属负责人审批", "系统管理员复核"),
                List.of("账号申请单：AUTH-REQ-001"),
                missingEvidence,
                missingCapabilities,
                List.of("GENERAL_MANAGER", "ADMIN"),
                readyForBlockingApproval ? "可进入阻断式审批" : "缺少前置材料",
                applicationStatus,
                readyForBlockingApproval,
                "applicationBoundary=stored pre-approval application, not yet an enforcing IAM workflow",
                readyForBlockingApproval ? "提交审批人复核并冻结账号动作放行条件" : "补齐前置材料",
                "高",
                readyForBlockingApproval ? "green" : "red",
                "AUTH-PRE-TASK-202607090001-0001",
                "2026-07-09 09:00:00",
                List.of("抛光", "雕刻"),
                "DISABLE"
        );
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table auth_account_pre_approval_application (
                        id bigint primary key auto_increment,
                        application_code varchar(64) not null,
                        draft_code varchar(128) not null,
                        readiness_code varchar(64) not null,
                        action_type varchar(64) not null,
                        target_username varchar(64) not null,
                        applicant varchar(128) not null,
                        applicant_role varchar(64) not null,
                        target_operator varchar(128) not null default '',
                        target_role varchar(64) not null default '',
                        target_data_scope varchar(64) not null default '',
                        target_customer_scope varchar(512) not null default '',
                        target_enabled boolean null,
                        demo_account boolean null,
                        reason varchar(512) not null default '',
                        required_evidence text not null,
                        provided_evidence text not null,
                        missing_evidence text not null,
                        missing_capabilities text not null,
                        approver_roles varchar(512) not null default '',
                        blocking_status varchar(64) not null,
                        application_status varchar(64) not null,
                        approval_task_code varchar(64) not null default '',
                        ready_for_blocking_approval boolean not null default false,
                        source_summary text not null,
                        next_action varchar(512) not null default '',
                        risk_level varchar(32) not null default '',
                        tone varchar(32) not null default '',
                        submitted_at varchar(32) not null,
                        target_allowed_processes text null,
                        status_action varchar(16) not null default '',
                        unique key uk_auth_pre_approval_application_code (application_code)
                    )
                    """);
        }
    }
}
