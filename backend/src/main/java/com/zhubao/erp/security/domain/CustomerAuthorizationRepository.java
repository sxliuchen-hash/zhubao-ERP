package com.zhubao.erp.security.domain;

import java.util.List;

public interface CustomerAuthorizationRepository {

    /** Grant a user access to a customer (idempotent on username + customerCode). */
    CustomerAuthorization grant(CustomerAuthorization authorization);

    /** Revoke a user's access to a customer. Returns true if a row was removed. */
    boolean revoke(String username, String customerCode);

    List<CustomerAuthorization> findByUsername(String username);

    List<CustomerAuthorization> findAll();
}
