package com.zhubao.erp.scan.web;

import com.zhubao.erp.scan.app.OperationProgressService;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.app.ScanContextService;
import com.zhubao.erp.scan.domain.ScanContextRequest;
import com.zhubao.erp.scan.domain.ScanIntent;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanAccountScopeTest {

    @Test
    void contextUsesTheSessionCustomerScopeInsteadOfTheStoredName() {
        ScanContextService scans = mock(ScanContextService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        ScanController controller = new ScanController(mock(ScanCodeParser.class), scans, mock(OperationProgressService.class), sessions);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER)).thenReturn("Bearer token");
        when(sessions.operatorContext("Bearer token")).thenReturn(Optional.of(
                new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "token", List.of("1001"))));
        when(sessions.currentAccount("Bearer token")).thenReturn(Optional.of(
                new AuthUserAccount("sales", "hash", "业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, true, false, List.of("恒信珠宝"))));

        controller.context(new ScanContextRequest("MD-1", null, ScanIntent.LOOKUP), request);

        ArgumentCaptor<AuthUserAccount> account = ArgumentCaptor.forClass(AuthUserAccount.class);
        verify(scans).query(org.mockito.ArgumentMatchers.any(), account.capture());
        assertThat(account.getValue().customerScope()).containsExactly("1001");
    }
}
