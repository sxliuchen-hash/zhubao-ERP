package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.UnpricedItem;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 财务读取的客户范围。全公司、授权客户集合和单个客户分开判断。
 * 空的客户范围不是全公司，空的 customerId 也不能绕过授权。
 */
@Component
public class FinanceReadAuthorizer {

    public void assertSignedIn(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String role = request.getHeader(OperatorContextResolver.ROLE_HEADER);
        boolean signedIn = authorization != null && !authorization.isBlank();
        boolean explicitRole = role != null && !role.isBlank();
        if (!signedIn && !explicitRole) {
            throw new PermissionDeniedException("请先登录后再查看财务");
        }
    }

    /** 工资、固定资产、消耗品和应付不按客户分摊，只允许全公司范围。 */
    public void assertCompany(OperatorContext context) {
        if (context.dataScope() != DataScope.ALL) {
            throw new PermissionDeniedException("公司工资、固定资产、消耗品和应付只对全公司数据范围开放");
        }
    }

    public void assertCustomer(OperatorContext context, long customerId, String customerName) {
        if (context.dataScope() == DataScope.ALL) {
            return;
        }
        if (context.customerScope().isEmpty()) {
            throw new PermissionDeniedException("当前账号没有授权客户，不能查看财务");
        }
        if (!customerVisible(context, customerId, customerName)) {
            throw new PermissionDeniedException("无权查看该客户的财务");
        }
    }

    public boolean customerVisible(OperatorContext context, long customerId, String customerName) {
        if (context.dataScope() == DataScope.ALL) {
            return true;
        }
        return matches(context.customerScope(), customerId, customerName);
    }

    public List<PriceAgreement> visibleAgreements(OperatorContext context, List<PriceAgreement> rows) {
        if (context.dataScope() == DataScope.ALL) {
            return rows;
        }
        if (context.customerScope().isEmpty()) {
            throw new PermissionDeniedException("当前账号没有授权客户，不能查看财务");
        }
        return rows.stream()
                .filter(row -> matches(context.customerScope(), row.customerId(), row.customerName()))
                .toList();
    }

    public List<UnpricedItem> visibleUnpriced(OperatorContext context, List<UnpricedItem> rows) {
        if (context.dataScope() == DataScope.ALL) {
            return rows;
        }
        if (context.customerScope().isEmpty()) {
            throw new PermissionDeniedException("当前账号没有授权客户，不能查看财务");
        }
        return rows.stream()
                .filter(row -> row.customerId() != null && matches(context.customerScope(), row.customerId(), row.customerName()))
                .toList();
    }

    private boolean matches(List<String> scope, long customerId, String customerName) {
        String idText = Long.toString(customerId);
        String name = customerName == null ? "" : customerName.trim();
        for (String token : scope) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String value = token.trim();
            if (value.equals(idText)) {
                return true;
            }
            if (!name.isEmpty() && name.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
