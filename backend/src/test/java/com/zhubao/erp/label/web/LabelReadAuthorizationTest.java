package com.zhubao.erp.label.web;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.app.LabelTemplateService;
import com.zhubao.erp.label.domain.ReprintLabelCommand;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabelReadAuthorizationTest {

    @Test
    void storedLabelQueriesUseTheDocumentReadBoundary() {
        LabelService labels = mock(LabelService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext allowed = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", java.util.List.of("1001"));
        when(resolver.requireSession(request)).thenReturn(allowed);
        LabelController controller = new LabelController(labels, mock(LabelTemplateService.class), resolver, access);

        assertThat(controller.motherLabel("MD-1", null, request).code()).isZero();
        verify(access).assertCanRead(allowed, "MD-1");
        verify(labels).generateMotherLabel("MD-1", null);

        doThrow(new PermissionDeniedException("单号不在当前账号的业务数据范围内"))
                .when(access).assertCanRead(allowed, "ZD-X");
        assertThatThrownBy(() -> controller.workOrderLabel("ZD-X", "TPL", request))
                .isInstanceOf(PermissionDeniedException.class);
        verify(labels, never()).generateWorkOrderLabel("ZD-X", "TPL");

        assertThatThrownBy(() -> controller.reprint(new ReprintLabelCommand("WORK_ORDER", "ZD-X", "补打", "业务员"), request))
                .isInstanceOf(PermissionDeniedException.class);
        verify(labels, never()).reprint(org.mockito.ArgumentMatchers.any());
    }
}
