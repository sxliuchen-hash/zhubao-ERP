package com.zhubao.erp.label.web;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.app.LabelTemplateService;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
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
    void 采用新保存要求需要标签及档案维护权限() {
        var labels = mock(LabelService.class); var resolver = mock(OperatorContextResolver.class);
        var operator = new OperatorContext("业务员", RoleCode.SALES, DataScope.ALL);
        when(resolver.requireSession(org.mockito.ArgumentMatchers.any())).thenReturn(operator);
        var controller = new LabelController(labels, mock(LabelTemplateService.class), resolver, mock(BusinessDataAccess.class));
        var editions = mock(com.zhubao.erp.label.app.LabelStorageEditionService.class);
        var guard = mock(com.zhubao.erp.security.app.PermissionGuard.class);
        controller.setStorageEditions(editions, guard);
        doThrow(new PermissionDeniedException("无档案维护权限")).when(guard).assertAllowed(com.zhubao.erp.security.domain.PermissionCode.MASTER_DATA_MANAGE, operator);
        assertThatThrownBy(() -> controller.adoptStorage("MD-1", new LabelController.AdoptStorage("adopt-001", 0, "换贴", ""), mock(HttpServletRequest.class))).isInstanceOf(PermissionDeniedException.class);
        verify(editions, never()).adopt(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 寄存通用标签小写类型也必须核对批次权限() {
        var labels = mock(LabelService.class);
        var resolver = mock(OperatorContextResolver.class);
        when(resolver.requireSession(org.mockito.ArgumentMatchers.any())).thenReturn(new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.ALL));
        var controller = new LabelController(labels, mock(LabelTemplateService.class), resolver, mock(BusinessDataAccess.class));
        controller.setStockAccess(mock(com.zhubao.erp.inventory.app.MaterialStockService.class), mock(com.zhubao.erp.security.app.AuthSessionService.class));
        assertThatThrownBy(() -> controller.generate(new GenerateLabelCommand("stock_batch", "PC-REAL", java.util.List.of(), "", "", ""), mock(HttpServletRequest.class)))
                .hasMessageContaining("请先登录");
        verify(labels, never()).generate(org.mockito.ArgumentMatchers.any());
    }

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

    @Test
    void genericGenerateChecksStoredOrdersAndStillBuildsUnknownCodes() {
        LabelService labels = mock(LabelService.class);
        OperatorContextResolver resolver = mock(OperatorContextResolver.class);
        BusinessDataAccess access = mock(BusinessDataAccess.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OperatorContext allowed = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", java.util.List.of("1001"));
        when(resolver.requireSession(request)).thenReturn(allowed);
        LabelController controller = new LabelController(labels, mock(LabelTemplateService.class), resolver, access);
        GenerateLabelCommand stored = new GenerateLabelCommand("WORK_ORDER", "ZD-1", java.util.List.of(), null, null, null);
        GenerateLabelCommand unknown = new GenerateLabelCommand("OUTBOUND", "CK-NEW", java.util.List.of(), null, null, null);

        assertThat(controller.generate(unknown, request).code()).isZero();
        verify(access).assertCanReadIfStored(allowed, "CK-NEW");
        verify(labels).generate(unknown);

        doThrow(new PermissionDeniedException("单号不在当前账号的业务数据范围内"))
                .when(access).assertCanReadIfStored(allowed, "ZD-1");
        assertThatThrownBy(() -> controller.generate(stored, request))
                .isInstanceOf(PermissionDeniedException.class);
        verify(labels, never()).generate(stored);
    }
}
