package com.zhubao.erp.security.domain;

import java.util.List;

public interface CustomerAuthorizationRepository {

    /** 按用户名和客户编码幂等授予客户访问权限。 */
    CustomerAuthorization grant(CustomerAuthorization authorization);

    /** 撤销客户访问权限，返回是否删除了授权行。 */
    boolean revoke(String username, String customerCode);

    List<CustomerAuthorization> findByUsername(String username);

    List<CustomerAuthorization> findAll();

    /** 替换指定账号授权；调用方负责事务或失败补偿。 */
    default void replace(String username, List<CustomerAuthorization> authorizations) {
        for (CustomerAuthorization current : findByUsername(username)) {
            revoke(username, current.customerCode());
        }
        authorizations.forEach(this::grant);
    }
}
