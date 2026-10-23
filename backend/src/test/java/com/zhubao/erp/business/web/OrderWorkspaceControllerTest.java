package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.OrderWorkspaceQueryService;
import com.zhubao.erp.business.domain.OrderWorkspacePage;
import com.zhubao.erp.common.error.GlobalExceptionHandler;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderWorkspaceControllerTest {

    @Mock
    private OrderWorkspaceQueryService service;
    @Mock
    private OperatorContextResolver resolver;
    @Mock
    private AuthSessionService sessions;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new OrderWorkspaceController(service, resolver, sessions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        when(resolver.requireSession(any())).thenThrow(new PermissionDeniedException("请先登录后再访问业务数据"));
        mvc.perform(get("/api/business/order-workspace")).andExpect(status().isForbidden());
    }

    @Test
    void invalidDateIsAParameterErrorAndResponseHasNoAmount() throws Exception {
        when(resolver.requireSession(any())).thenReturn(new OperatorContext("办公室", RoleCode.WORKSHOP_LEADER, DataScope.ALL));
        when(sessions.session(any())).thenReturn(Optional.of(session()));
        mvc.perform(get("/api/business/order-workspace").param("dueBefore", "2026/10/23"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("截止日期需使用 yyyy-MM-dd"));

        when(service.query(any(), any(), any())).thenReturn(new OrderWorkspacePage(
                List.of(), 1, 20, 0, Map.of("ALL", 0), List.of("ORDERS"), "2026-10-23T20:00:00+08:00"
        ));
        mvc.perform(get("/api/business/order-workspace").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(0))
                .andExpect(jsonPath("$.data.amount").doesNotExist());
    }

    private AuthSession session() {
        return new AuthSession(
                "token", "leader", "办公室", "WORKSHOP_LEADER", "车间组长", "ALL", "全部",
                "2026-11-01 00:00:00", false, "", "", false, false, List.of(),
                List.of("progress"), List.of(), List.of(), List.of(), "", "", List.of()
        );
    }
}
