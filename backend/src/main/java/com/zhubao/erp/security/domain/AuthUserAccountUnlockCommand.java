package com.zhubao.erp.security.domain;

public record AuthUserAccountUnlockCommand(
        String reason,
        String preApprovalApplicationCode
) {
    public AuthUserAccountUnlockCommand(String reason) {
        this(reason, null);
    }
}
