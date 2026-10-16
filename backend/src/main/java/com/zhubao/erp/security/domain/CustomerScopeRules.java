package com.zhubao.erp.security.domain;

/**
 * 客户名单是否覆盖某个客户。未填写名单的部门、班组或本人范围视为不限制客户；
 * 授权客户范围必须命中名单。
 */
public final class CustomerScopeRules {
    private CustomerScopeRules() {
    }

    public static boolean allowed(OperatorContext operator, Long customerId, String customerName) {
        if (operator == null || operator.dataScope() == DataScope.ALL) {
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
}
