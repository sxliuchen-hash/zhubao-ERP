package com.zhubao.erp.approval.web;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.common.error.GlobalExceptionHandler;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    private ApprovalService approvalService;
    @Mock
    private AuthSessionService authSessionService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ApprovalController controller = new ApprovalController(approvalService, authSessionService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void anonymousAndHeaderIdentityCannotReadApprovals() throws Exception {
        when(authSessionService.currentAccount(nullable(String.class))).thenReturn(Optional.empty());

        mvc.perform(get("/api/approvals/tasks")).andExpect(status().isForbidden());
        mvc.perform(get("/api/approvals/tasks/pending")
                .header("X-Zhubao-Role", "ADMIN")
                .header("X-Zhubao-Operator", "伪造管理员")).andExpect(status().isForbidden());
        mvc.perform(get("/api/approvals/tasks/LOSS-1")
                .header("X-Zhubao-Role", "GENERAL_MANAGER")).andExpect(status().isForbidden());
    }

    @Test
    void decisionUsesTheSessionAccountInsteadOfTheRequestOperator() throws Exception {
        AuthUserAccount leader = new AuthUserAccount(
                "leader", "hash", "车间组长 马杰", RoleCode.WORKSHOP_LEADER, DataScope.TEAM, true, false);
        when(authSessionService.currentAccount(nullable(String.class))).thenReturn(Optional.of(leader));
        when(approvalService.visibleTasks(any())).thenReturn(List.of());

        mvc.perform(get("/api/approvals/tasks").header("Authorization", "Bearer session-token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/approvals/tasks/LOSS-1/decisions")
                        .header("Authorization", "Bearer session-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"operator\":\"伪造审批人\",\"comment\":\"同意\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ApprovalActor> actor = ArgumentCaptor.forClass(ApprovalActor.class);
        verify(approvalService).decide(any(), any(), actor.capture());
        assertThat(actor.getValue().operator()).isEqualTo("车间组长 马杰");
        assertThat(actor.getValue().username()).isEqualTo("leader");
        assertThat(actor.getValue().role()).isEqualTo(RoleCode.WORKSHOP_LEADER);
    }
}
