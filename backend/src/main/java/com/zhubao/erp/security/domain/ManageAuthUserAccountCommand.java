package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ManageAuthUserAccountCommand(
        @NotBlank String username,
        String password,
        @NotBlank String operator,
        @NotBlank String role,
        @NotBlank String dataScope,
        List<String> customerScope,
        boolean enabled,
        boolean demoAccount,
        String preApprovalApplicationCode,
        List<String> allowedProcesses,
        List<String> coverDuties,
        String teamCode,
        String departmentCode
) {
    public ManageAuthUserAccountCommand {
        allowedProcesses = allowedProcesses == null ? null : List.copyOf(allowedProcesses);
        coverDuties = coverDuties == null ? null : List.copyOf(coverDuties);
        teamCode = teamCode == null ? null : teamCode.trim();
        departmentCode = departmentCode == null ? null : departmentCode.trim();
    }

    public ManageAuthUserAccountCommand(
            String username,
            String password,
            String operator,
            String role,
            String dataScope,
            List<String> customerScope,
            boolean enabled,
            boolean demoAccount,
            String preApprovalApplicationCode,
            List<String> allowedProcesses,
            List<String> coverDuties
    ) {
        this(username, password, operator, role, dataScope, customerScope, enabled, demoAccount,
                preApprovalApplicationCode, allowedProcesses, coverDuties, null, null);
    }

    public ManageAuthUserAccountCommand(
            String username,
            String password,
            String operator,
            String role,
            String dataScope,
            List<String> customerScope,
            boolean enabled,
            boolean demoAccount,
            String preApprovalApplicationCode,
            List<String> allowedProcesses
    ) {
        this(username, password, operator, role, dataScope, customerScope, enabled, demoAccount, preApprovalApplicationCode, allowedProcesses, null, null, null);
    }

    public ManageAuthUserAccountCommand(
            String username,
            String password,
            String operator,
            String role,
            String dataScope,
            List<String> customerScope,
            boolean enabled,
            boolean demoAccount,
            String preApprovalApplicationCode
    ) {
        this(username, password, operator, role, dataScope, customerScope, enabled, demoAccount, preApprovalApplicationCode, null, null);
    }

    public ManageAuthUserAccountCommand(
            String username,
            String password,
            String operator,
            String role,
            String dataScope,
            List<String> customerScope,
            boolean enabled,
            boolean demoAccount
    ) {
        this(username, password, operator, role, dataScope, customerScope, enabled, demoAccount, null, null, null);
    }

    public ManageAuthUserAccountCommand(
            String username,
            String password,
            String operator,
            String role,
            String dataScope,
            boolean enabled,
            boolean demoAccount
    ) {
        this(username, password, operator, role, dataScope, null, enabled, demoAccount, null, null, null);
    }
}
