package com.zhubao.erp.security.domain;

import java.util.Optional;

/**
 * 客户入口绑定。写入必须走身份动作事务，这里只提供读取。
 */
public interface CustomerPortalBindingRepository {

    Optional<CustomerPortalBinding> findByUsername(String username);

    Optional<CustomerPortalBinding> findByCustomerId(long customerId);
}
