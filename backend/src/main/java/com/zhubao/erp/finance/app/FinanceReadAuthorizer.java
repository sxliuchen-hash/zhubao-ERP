package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.UnpricedItem;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 财务读写的客户范围。全公司、授权客户集合和单个客户分开判断。
 * 空的客户范围不是全公司，空的 customerId 也不能绕过授权。
 */
@Component
public class FinanceReadAuthorizer {

    private final FinanceRepository finance;
    private final BusinessDocumentRepository documents;

    public FinanceReadAuthorizer() {
        this(null, null);
    }

    @Autowired
    public FinanceReadAuthorizer(FinanceRepository finance, BusinessDocumentRepository documents) {
        this.finance = finance;
        this.documents = documents;
    }

    /** 写命令只接受客户编号或持久化的真实客户名称，不信任请求提交的显示名称。 */
    public void assertCustomerWrite(OperatorContext context, Long customerId) {
        if (customerId == null) {
            assertCompany(context);
            return;
        }
        if (customerVisible(context, customerId, "")) {
            return;
        }
        if (documents != null && documents.listMotherOrders().stream()
                .anyMatch(row -> customerId.equals(row.customerId())
                        && customerVisible(context, customerId, row.customerName()))) {
            return;
        }
        if (finance != null && (finance.listAgreements().stream()
                .anyMatch(row -> customerId.equals(row.customerId()) && customerVisible(context, customerId, row.customerName()))
                || finance.listReceivables().stream()
                .anyMatch(row -> customerId.equals(row.customerId()) && customerVisible(context, customerId, row.customerName()))
                || finance.listReceipts().stream()
                .anyMatch(row -> customerId.equals(row.customerId()) && customerVisible(context, customerId, row.customerName())))) {
            return;
        }
        assertCustomer(context, customerId, "");
    }

    /** 冲销请求没有客户编号，必须由应收原行确定授权范围。 */
    public void assertReceivableWrite(OperatorContext context, String receivableCode) {
        if (finance == null) {
            throw new PermissionDeniedException("缺少应收归属信息，不能冲销");
        }
        var original = finance.findReceivableByCode(receivableCode)
                .orElseThrow(() -> new BusinessException("找不到要冲销的账单行"));
        assertCustomer(context, original.customerId(), original.customerName());
    }

    public void assertSignedIn(HttpServletRequest request, OperatorContext context) {
        String role = request.getHeader(OperatorContextResolver.ROLE_HEADER);
        boolean explicitRole = role != null && !role.isBlank();
        // 头身份仅在解析器显式允许的环境使用；无效令牌不得退回默认管理员。
        if (context.headerBased() && !explicitRole) {
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
