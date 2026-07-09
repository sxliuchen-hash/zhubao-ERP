package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorContextResolverTest {

    private final NavigationMenuService navigationMenuService =
            new NavigationMenuService(new com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository());
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final PasswordExpiryPolicy passwordExpiryPolicy = new PasswordExpiryPolicy();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void resolvesHeaderContextWhenFallbackAllowed() {
        OperatorContextResolver resolver = new OperatorContextResolver(service(), true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(OperatorContextResolver.OPERATOR_HEADER, "库管员 何琳");
        request.addHeader(OperatorContextResolver.ROLE_HEADER, "WAREHOUSE_KEEPER");
        request.addHeader(OperatorContextResolver.DATA_SCOPE_HEADER, "TEAM");
        request.addHeader(OperatorContextResolver.CUSTOMER_SCOPE_HEADER, "ZD-20260706-001;ZD-20260706-002");

        OperatorContext context = resolver.resolve(request);

        assertThat(context.operator()).isEqualTo("库管员 何琳");
        assertThat(context.role()).isEqualTo(RoleCode.WAREHOUSE_KEEPER);
        assertThat(context.dataScope()).isEqualTo(DataScope.TEAM);
        assertThat(context.headerBased()).isTrue();
        assertThat(context.customerScope()).containsExactly("ZD-20260706-001", "ZD-20260706-002");
    }

    @Test
    void rejectsHeaderFallbackWhenDisabledWithoutBearerSession() {
        OperatorContextResolver resolver = new OperatorContextResolver(service(), false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(OperatorContextResolver.OPERATOR_HEADER, "系统管理员");
        request.addHeader(OperatorContextResolver.ROLE_HEADER, "ADMIN");
        request.addHeader(OperatorContextResolver.DATA_SCOPE_HEADER, "ALL");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("请求头身份兜底已关闭")
                .hasMessageContaining("登录会话");
    }

    @Test
    void resolvesBearerSessionWhenHeaderFallbackDisabled() {
        AuthSessionService service = service();
        AuthSession session = service.login(new AuthLoginCommand("leader", "leader123"));
        OperatorContextResolver resolver = new OperatorContextResolver(service, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthSessionService.AUTHORIZATION_HEADER, "Bearer " + session.token());

        OperatorContext context = resolver.resolve(request);

        assertThat(context.operator()).isEqualTo("车间组长 马杰");
        assertThat(context.role()).isEqualTo(RoleCode.WORKSHOP_LEADER);
        assertThat(context.dataScope()).isEqualTo(DataScope.TEAM);
        assertThat(context.headerBased()).isFalse();
        assertThat(context.sessionToken()).isEqualTo(session.token());
    }

    @Test
    void rejectsInvalidAuthorizationSchemeBeforeHeaderFallback() {
        OperatorContextResolver resolver = new OperatorContextResolver(service(), true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthSessionService.AUTHORIZATION_HEADER, "Basic bad-token");
        request.addHeader(OperatorContextResolver.ROLE_HEADER, "ADMIN");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Bearer token");
    }

    private AuthSessionService service() {
        return service(new InMemoryAuthUserAccountRepository(passwordHasher));
    }

    private AuthSessionService service(AuthUserAccountRepository userRepository) {
        return new AuthSessionService(
                navigationMenuService,
                new InMemoryAuthAuditRepository(),
                userRepository,
                passwordHasher,
                passwordExpiryPolicy,
                passwordPolicy
        );
    }
}
