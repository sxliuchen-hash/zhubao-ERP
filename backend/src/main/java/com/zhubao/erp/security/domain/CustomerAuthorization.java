package com.zhubao.erp.security.domain;

/**
 * A persisted authorization granting a user visibility of a specific customer's data.
 *
 * <p>Replaces the loose {@code customerScope} text field on the account with a maintainable
 * authorization table: a {@code CUSTOMER_RANGE} user's effective customer scope is the set of
 * customer codes granted here. When a user has no rows, the system falls back to the account's
 * {@code customerScope} field for backward compatibility.</p>
 */
public record CustomerAuthorization(
        String username,
        String customerCode,
        String customerName,
        String grantedBy,
        String grantedAt
) {
}
