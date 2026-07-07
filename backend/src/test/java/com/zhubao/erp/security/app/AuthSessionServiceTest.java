package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthSessionServiceTest {

    @Test
    void logsInDemoUserAndResolvesOperatorContextFromBearerToken() {
        AuthSessionService service = new AuthSessionService();

        AuthSession session = service.login(new AuthLoginCommand("leader", "leader123"));
        OperatorContext context = service.operatorContext("Bearer " + session.token()).orElseThrow();

        assertThat(session.username()).isEqualTo("leader");
        assertThat(session.operator()).isEqualTo("车间组长 马杰");
        assertThat(session.role()).isEqualTo("WORKSHOP_LEADER");
        assertThat(session.menuCodes()).contains("production", "quality", "approval");
        assertThat(context.operator()).isEqualTo("车间组长 马杰");
        assertThat(context.role()).isEqualTo(RoleCode.WORKSHOP_LEADER);
        assertThat(context.headerBased()).isFalse();
        assertThat(context.sessionToken()).isEqualTo(session.token());
    }

    @Test
    void rejectsWrongPasswordAndInvalidAuthorizationScheme() {
        AuthSessionService service = new AuthSessionService();

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "wrong")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");

        assertThatThrownBy(() -> service.operatorContext("Basic bad-token"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Bearer token");
    }

    @Test
    void logoutInvalidatesSessionToken() {
        AuthSessionService service = new AuthSessionService();
        AuthSession session = service.login(new AuthLoginCommand("operator", "operator123"));

        service.logout("Bearer " + session.token());

        assertThatThrownBy(() -> service.session("Bearer " + session.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("登录会话不存在或已退出");
    }

    @Test
    void exposesSortedDemoUsernames() {
        AuthSessionService service = new AuthSessionService();

        assertThat(service.demoUsernames()).containsExactly("admin", "leader", "operator", "qc", "warehouse");
    }
}
