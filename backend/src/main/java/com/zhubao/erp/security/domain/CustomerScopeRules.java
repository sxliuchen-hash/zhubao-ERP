package com.zhubao.erp.security.domain;

/**
 * 客户名单是否覆盖某个客户。仅客户名单范围必须配置客户；其他范围没有名单时不附加客户限制。
 */
public final class CustomerScopeRules {
    private CustomerScopeRules() {
    }

    public static boolean allowed(OperatorContext operator, Long customerId, String customerName) {
        if (operator == null) {
            return false;
        }
        if (operator.dataScope() == DataScope.ALL) {
            return true;
        }
        if (operator.customerScope().isEmpty()) {
            return operator.dataScope() != DataScope.CUSTOMER_RANGE;
        }
        String idText = customerId == null ? "" : customerId.toString();
        String prefixed = idText.isEmpty() ? "" : "客户#" + idText;
        String name = customerName == null ? "" : customerName.trim();
        return operator.customerScope().stream()
                .map(item -> item == null ? "" : item.trim())
                .anyMatch(value -> !value.isEmpty()
                        && (value.equals(name) || value.equals(idText) || value.equals(prefixed)));
    }

    /**
     * 两边都写了客户编号且不相同。缺编号的旧单据不据此判成另一家客户。
     */
    public static boolean identityConflicts(Long left, Long right) {
        return left != null && right != null && !left.equals(right);
    }
}
