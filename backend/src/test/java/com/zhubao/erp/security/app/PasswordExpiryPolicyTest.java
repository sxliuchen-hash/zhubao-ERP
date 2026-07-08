package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordExpiryPolicyTest {

    private final PasswordExpiryPolicy policy = new PasswordExpiryPolicy();

    @Test
    void expiresProductionPasswordAfterNinetyDays() {
        Instant changedAt = Instant.parse("2026-01-01T00:00:00Z");
        AuthUserAccount account = account(false, changedAt);

        assertThat(policy.isExpired(account, changedAt.plus(Duration.ofDays(89)))).isFalse();
        assertThat(policy.isExpired(account, changedAt.plus(Duration.ofDays(90)))).isTrue();
        assertThat(policy.expiresAt(account)).isEqualTo(changedAt.plus(Duration.ofDays(90)));
    }

    @Test
    void demoSeedAccountDoesNotExpireInThisBoundary() {
        Instant changedAt = Instant.parse("2025-01-01T00:00:00Z");
        AuthUserAccount account = account(true, changedAt);

        assertThat(policy.isExpired(account, Instant.parse("2026-07-08T00:00:00Z"))).isFalse();
    }

    @Test
    void missingChangedAtExpiresProductionAccount() {
        AuthUserAccount account = account(false, null);

        assertThat(policy.isExpired(account, Instant.parse("2026-07-08T00:00:00Z"))).isTrue();
    }

    private AuthUserAccount account(boolean demoAccount, Instant changedAt) {
        return new AuthUserAccount(
                "planner",
                "hash",
                "计划员 陈敏",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                demoAccount,
                changedAt,
                0,
                null
        );
    }
}
