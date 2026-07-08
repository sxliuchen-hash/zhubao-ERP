package com.zhubao.erp.approval.app;

import com.zhubao.erp.approval.domain.AccountPreApprovalTaskCommand;
import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalServiceTest {

    private final InMemoryApprovalTaskRepository repository = new InMemoryApprovalTaskRepository();
    private final ApprovalService service = new ApprovalService(repository);

    @Test
    void createsWaitingApprovalTaskForOverLimitLossTrial() {
        ApprovalTask task = service.createLossApprovalTask(lossCommand(), lossResult(true));

        assertThat(task.approvalCode()).isEqualTo("LOSS-UNIT-001");
        assertThat(task.scene()).isEqualTo("LOSS_APPROVAL");
        assertThat(task.businessCode()).isEqualTo("ZD-UNIT-001");
        assertThat(task.status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
        assertThat(task.locked()).isFalse();
        assertThat(task.formalPayroll()).isFalse();
        assertThat(task.sourceSummary()).contains(
                "lossWeight=4.600",
                "excessLossWeight=2.177",
                "trialWage=31.74",
                "formalPayroll=false"
        );
        assertThat(service.findPending()).containsExactly(task);
    }

    @Test
    void autoApprovesLossTaskWhenLossIsWithinConfiguredThreshold() {
        ApprovalTask task = service.createLossApprovalTask(lossCommand(), lossResult(false));

        assertThat(task.status()).isEqualTo(ApprovalStatus.AUTO_APPROVED);
        assertThat(task.locked()).isTrue();
        assertThat(task.decisionOperator()).isEqualTo("SYSTEM");
        assertThat(service.findPending()).isEmpty();
    }

    @Test
    void createsWaitingAccountManagementApprovalSummary() {
        ApprovalTask task = service.createAccountManagementApprovalTask(
                "planner",
                "创建账号",
                "系统管理员",
                "ADMIN",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                "恒信珠宝|雅辰饰品",
                true,
                false
        );

        assertThat(task.approvalCode()).startsWith("AUTH-ACC-APP-");
        assertThat(task.scene()).isEqualTo("ACCOUNT_MANAGEMENT");
        assertThat(task.businessCode()).isEqualTo("planner");
        assertThat(task.status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
        assertThat(task.applicant()).isEqualTo("系统管理员");
        assertThat(task.approver()).isEqualTo("总经理 / IT 管理员");
        assertThat(task.ownerRole()).isEqualTo("GENERAL_MANAGER");
        assertThat(task.formalPayroll()).isFalse();
        assertThat(task.locked()).isFalse();
        assertThat(task.sourceSummary()).contains(
                "action=创建账号",
                "applicantRole=ADMIN",
                "targetOperator=计划员 陈敏",
                "targetRole=SALES",
                "targetDataScope=CUSTOMER_RANGE",
                "targetCustomerScope=恒信珠宝|雅辰饰品",
                "enabled=true",
                "demoAccount=false",
                "not a blocking IAM workflow"
        );
        assertThat(service.findPending()).contains(task);
    }

    @Test
    void createsWaitingAccountPreApprovalHandoffTask() {
        ApprovalTask task = service.createAccountPreApprovalTask(new AccountPreApprovalTaskCommand(
                "AUTH-PRE-APP-UNIT-001",
                "AUTH-PRE-CREATE-001",
                "CREATE_ACCOUNT",
                "planner",
                "admin",
                "ADMIN",
                "Planner Chen",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("Customer-A", "Customer-B"),
                "WAITING_EVIDENCE",
                "missing evidence",
                false,
                List.of("manager approval"),
                List.of("formal e-signature"),
                List.of("GENERAL_MANAGER", "ADMIN"),
                "HIGH",
                "source=pre-approval application"
        ));

        assertThat(task.approvalCode()).startsWith("AUTH-PRE-TASK-");
        assertThat(task.scene()).isEqualTo("ACCOUNT_PRE_APPROVAL");
        assertThat(task.businessCode()).isEqualTo("AUTH-PRE-APP-UNIT-001");
        assertThat(task.status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
        assertThat(task.applicant()).isEqualTo("admin");
        assertThat(task.approver()).isEqualTo("GENERAL_MANAGER|ADMIN");
        assertThat(task.ownerRole()).isEqualTo("GENERAL_MANAGER");
        assertThat(task.sourceStatus()).isEqualTo("WAITING_EVIDENCE");
        assertThat(task.evidenceCode()).isEqualTo("AUTH-PRE-APP-UNIT-001");
        assertThat(task.sourceSummary()).contains(
                "applicationCode=AUTH-PRE-APP-UNIT-001",
                "targetUsername=planner",
                "targetCustomerScope=Customer-A|Customer-B",
                "missingEvidence=manager approval",
                "missingCapabilities=formal e-signature",
                "not final IAM approval"
        );
    }

    @Test
    void decidesWaitingApprovalOnceAndLocksAuditSnapshot() {
        service.createLossApprovalTask(lossCommand(), lossResult(true));

        ApprovalTask decided = service.decide(
                "LOSS-UNIT-001",
                new ApprovalDecisionCommand("APPROVE", "workshop-leader", "Evidence checked.", "ATT-APPROVAL-001")
        );

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.locked()).isTrue();
        assertThat(decided.decisionOperator()).isEqualTo("workshop-leader");
        assertThat(decided.decisionComment()).isEqualTo("Evidence checked.");
        assertThat(decided.evidenceCode()).isEqualTo("ATT-APPROVAL-001");
        assertThat(service.findPending()).isEmpty();
        assertThatThrownBy(() -> service.decide(
                "LOSS-UNIT-001",
                new ApprovalDecisionCommand("REJECT", "manager", "late change", null)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("already decided");
    }

    private ApproveLossCommand lossCommand() {
        return new ApproveLossCommand(
                "LOSS-UNIT-001",
                "MD-UNIT-001",
                "ZD-UNIT-001",
                1001L,
                null,
                "MAT-UNIT",
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
                "HO-UNIT-001",
                "unit test"
        );
    }

    private LossApprovalResult lossResult(boolean overLimit) {
        return new LossApprovalResult(
                "LOSS-UNIT-001",
                "MD-UNIT-001",
                "ZD-UNIT-001",
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
                "HO-UNIT-001"
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
