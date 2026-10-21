package com.zhubao.erp.approval.app;

import com.zhubao.erp.approval.domain.AccountPreApprovalTaskCommand;
import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalServiceTest {

    private final InMemoryApprovalTaskRepository repository = new InMemoryApprovalTaskRepository();
    private final ApprovalService service = new ApprovalService(repository);

    @Test
    void lossTaskRequiresNamedLeaderAndDocumentScope() {
        AtomicBoolean visible = new AtomicBoolean(false);
        BusinessDataAccess documents = new BusinessDataAccess(null, null) {
            @Override
            public boolean canRead(OperatorContext actor, String businessCode) {
                return visible.get() && "ZD-UNIT-001".equals(businessCode);
            }
        };
        ApprovalService scoped = new ApprovalService(new InMemoryApprovalTaskRepository(), documents);
        scoped.createLossApprovalTask(lossCommand(), lossResult(true));
        OperatorContext context = new OperatorContext("leader-a", RoleCode.WORKSHOP_LEADER, DataScope.TEAM);
        ApprovalActor assigned = new ApprovalActor("leader-a", "leader", RoleCode.WORKSHOP_LEADER, context);
        ApprovalActor other = new ApprovalActor("leader-b", "leader-b", RoleCode.WORKSHOP_LEADER, context);

        assertThat(scoped.visibleTasks(assigned)).isEmpty();
        assertThat(scoped.pendingTasks(assigned)).isEmpty();
        assertThatThrownBy(() -> scoped.decide("LOSS-UNIT-001",
                new ApprovalDecisionCommand("APPROVE", "", "", null), assigned))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("数据范围");

        visible.set(true);
        assertThat(scoped.pendingTasks(assigned)).hasSize(1);
        assertThat(scoped.visibleTasks(other)).isEmpty();
        assertThatThrownBy(() -> scoped.decide("LOSS-UNIT-001",
                new ApprovalDecisionCommand("APPROVE", "", "", null), other))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("未分配");
    }

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
    void decidesWaitingApprovalOnceAndRecordsTheSessionOperator() {
        service.createLossApprovalTask(lossCommand(), lossResult(true));
        ApprovalActor leader = actor("leader-a", "leader", RoleCode.WORKSHOP_LEADER);

        ApprovalTask decided = service.decide(
                "LOSS-UNIT-001",
                new ApprovalDecisionCommand("APPROVE", "伪造审批人", "Evidence checked.", "ATT-APPROVAL-001"),
                leader
        );

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.locked()).isTrue();
        assertThat(decided.decisionOperator()).isEqualTo("leader-a");
        assertThat(decided.decisionComment()).isEqualTo("Evidence checked.");
        assertThat(decided.evidenceCode()).isEqualTo("ATT-APPROVAL-001");
        assertThat(service.pendingTasks(leader)).isEmpty();
        assertThatThrownBy(() -> service.decide(
                "LOSS-UNIT-001",
                new ApprovalDecisionCommand("REJECT", "manager", "late change", null),
                leader
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复决策");
    }

    @Test
    void hidesTasksOutsideTheViewerSceneAndRejectsSelfApproval() {
        service.createLossApprovalTask(lossCommand(), lossResult(true));
        ApprovalTask account = service.createAccountManagementApprovalTask(
                "planner", "创建账号", "系统管理员", "ADMIN", "计划员", "SALES", "SELF", "", true, false);
        ApprovalActor leader = actor("leader-a", "leader", RoleCode.WORKSHOP_LEADER);
        ApprovalActor finance = actor("财务", "finance", RoleCode.FINANCE_STATISTICIAN);
        ApprovalActor applicant = actor("operator-a", "operator-a", RoleCode.WORKSHOP_LEADER);
        ApprovalActor manager = actor("总经理", "gm", RoleCode.GENERAL_MANAGER);

        assertThat(service.visibleTasks(leader)).extracting(ApprovalTask::approvalCode).containsExactly("LOSS-UNIT-001");
        assertThat(service.pendingTasks(leader)).extracting(ApprovalTask::scene).containsExactly("LOSS_APPROVAL");
        assertThat(service.visibleTasks(finance)).isEmpty();
        assertThat(service.visibleTasks(applicant)).extracting(ApprovalTask::approvalCode).containsExactly("LOSS-UNIT-001");
        assertThat(service.pendingTasks(applicant)).isEmpty();
        assertThatThrownBy(() -> service.visibleTask(account.approvalCode(), leader))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权查看");
        assertThatThrownBy(() -> service.decide(
                "LOSS-UNIT-001",
                new ApprovalDecisionCommand("APPROVE", "operator-a", "自己批", null),
                applicant
        )).isInstanceOf(PermissionDeniedException.class).hasMessageContaining("申请人不能审批");
        assertThatThrownBy(() -> service.decide(
                account.approvalCode(),
                new ApprovalDecisionCommand("APPROVE", "leader-a", "跨场景", null),
                leader
        )).isInstanceOf(PermissionDeniedException.class).hasMessageContaining("不能审批该场景");
        assertThatThrownBy(() -> service.decide(
                "LOSS-UNIT-001",
                new ApprovalDecisionCommand("APPROVE", "财务", "跨场景", null),
                finance
        )).isInstanceOf(PermissionDeniedException.class).hasMessageContaining("不能审批该场景");

        ApprovalTask acknowledged = service.decide(
                account.approvalCode(),
                new ApprovalDecisionCommand("APPROVE", "伪造总经理", "已阅", null),
                manager
        );
        assertThat(acknowledged.decisionOperator()).isEqualTo("总经理");
        assertThat(acknowledged.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(service.findByCode("LOSS-UNIT-001").status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
    }

    @Test
    void concurrentDecisionsSucceedOnlyOnce() throws Exception {
        service.createLossApprovalTask(lossCommand(), lossResult(true));
        ApprovalActor leader = actor("leader-a", "leader", RoleCode.WORKSHOP_LEADER);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    service.decide("LOSS-UNIT-001", new ApprovalDecisionCommand("APPROVE", "伪造", "并发", null), leader);
                    return true;
                } catch (BusinessException ex) {
                    return false;
                }
            }));
        }
        ready.await();
        start.countDown();
        int success = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                success++;
            }
        }
        pool.shutdown();
        assertThat(success).isEqualTo(1);
        assertThat(service.findByCode("LOSS-UNIT-001").status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(service.findByCode("LOSS-UNIT-001").decisionOperator()).isEqualTo("leader-a");
    }

    private ApprovalActor actor(String operator, String username, RoleCode role) {
        return new ApprovalActor(operator, username, role);
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
