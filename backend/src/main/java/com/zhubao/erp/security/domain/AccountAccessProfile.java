package com.zhubao.erp.security.domain;

import java.util.List;

/** 脱敏后的账号授权清单。不含密码、令牌和密码哈希。 */
public record AccountAccessProfile(
        String username,
        String operator,
        String role,
        String dataScope,
        List<String> customerScope,
        List<String> allowedProcesses,
        List<String> coverDuties,
        String teamCode,
        String departmentCode,
        List<PermissionGrant> effectivePermissions
) {
    public AccountAccessProfile {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
        allowedProcesses = allowedProcesses == null ? List.of() : List.copyOf(allowedProcesses);
        coverDuties = coverDuties == null ? List.of() : List.copyOf(coverDuties);
        effectivePermissions = effectivePermissions == null ? List.of() : List.copyOf(effectivePermissions);
        teamCode = teamCode == null ? "" : teamCode;
        departmentCode = departmentCode == null ? "" : departmentCode;
    }

    public record PermissionGrant(String code, String label, String source) {
    }
}
