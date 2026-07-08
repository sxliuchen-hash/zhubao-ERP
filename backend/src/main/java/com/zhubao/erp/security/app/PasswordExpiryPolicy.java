package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.AuthUserAccount;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class PasswordExpiryPolicy {

    private static final Duration MAX_PASSWORD_AGE = Duration.ofDays(90);

    public boolean isExpired(AuthUserAccount account, Instant now) {
        if (account.demoAccount()) {
            return false;
        }
        return account.passwordChangedAt() == null || !expiresAt(account).isAfter(now);
    }

    public Instant expiresAt(AuthUserAccount account) {
        if (account.passwordChangedAt() == null) {
            return Instant.EPOCH;
        }
        return account.passwordChangedAt().plus(MAX_PASSWORD_AGE);
    }

    public long maxAgeDays() {
        return MAX_PASSWORD_AGE.toDays();
    }
}
