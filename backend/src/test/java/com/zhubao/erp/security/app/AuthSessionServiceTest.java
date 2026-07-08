package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthSessionServiceTest {

    private final NavigationMenuService navigationMenuService = new NavigationMenuService();

    @Test
    void logsInDemoUserAndResolvesOperatorContextFromBearerToken() {
        AuthSessionService service = service();

        AuthSession session = service.login(new AuthLoginCommand("leader", "leader123"));
        OperatorContext context = service.operatorContext("Bearer " + session.token()).orElseThrow();

        assertThat(session.username()).isEqualTo("leader");
        assertThat(session.operator()).isEqualTo("车间组长 马杰");
        assertThat(session.role()).isEqualTo("WORKSHOP_LEADER");
        assertThat(session.menuCodes()).contains("production", "quality", "approvals", "sample");
        assertThat(session.menuTree())
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("sample");
                    assertThat(item.buttonCodes()).contains("REGISTER_IMPORT_REVIEW");
                });
        assertThat(context.operator()).isEqualTo("车间组长 马杰");
        assertThat(context.role()).isEqualTo(RoleCode.WORKSHOP_LEADER);
        assertThat(context.headerBased()).isFalse();
        assertThat(context.sessionToken()).isEqualTo(session.token());
    }

    @Test
    void rejectsWrongPasswordAndInvalidAuthorizationScheme() {
        AuthSessionService service = service();

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "wrong")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");

        assertThatThrownBy(() -> service.operatorContext("Basic bad-token"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Bearer token");
    }

    @Test
    void logoutInvalidatesSessionToken() {
        AuthSessionService service = service();
        AuthSession session = service.login(new AuthLoginCommand("operator", "operator123"));

        service.logout("Bearer " + session.token());

        assertThatThrownBy(() -> service.session("Bearer " + session.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("登录会话不存在或已退出");
    }

    @Test
    void recordsLoginSessionAndLogoutAuditEventsWithoutExposingFullToken() {
        AuthSessionService service = service();

        AuthSession session = service.login(new AuthLoginCommand("warehouse", "warehouse123"));
        service.session("Bearer " + session.token());
        service.logout("Bearer " + session.token());

        assertThat(service.auditEvents(10))
                .extracting("eventType")
                .contains("LOGIN", "SESSION_READ", "LOGOUT");
        assertThat(service.auditEvents(10))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN");
                    assertThat(event.username()).isEqualTo("warehouse");
                    assertThat(event.operator()).isEqualTo("库管员 何琳");
                    assertThat(event.result()).isEqualTo("SUCCESS");
                    assertThat(event.sessionTokenSuffix()).isNotEmpty();
                    assertThat(session.token()).endsWith(event.sessionTokenSuffix());
                    assertThat(event.sessionTokenSuffix()).isNotEqualTo(session.token());
                });
    }

    @Test
    void recordsFailedLoginAuditEvent() {
        AuthSessionService service = service();

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "bad-password")))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(service.auditEvents(5))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN");
                    assertThat(event.username()).isEqualTo("leader");
                    assertThat(event.result()).isEqualTo("DENIED");
                    assertThat(event.reason()).contains("用户名或密码错误");
                    assertThat(event.sessionTokenSuffix()).isEmpty();
                });
    }

    @Test
    void exposesSortedDemoUsernames() {
        AuthSessionService service = service();

        assertThat(service.demoUsernames()).containsExactly("admin", "leader", "operator", "qc", "warehouse");
    }

    private AuthSessionService service() {
        return new AuthSessionService(navigationMenuService, new InMemoryAuthAuditRepository());
    }
}
