package com.zhubao.erp.security.domain;

import java.util.List;

/** 账号配置使用的角色及其现行权限目录。 */
public record AuthAccountOptions(List<RoleOption> roles) {
    public record RoleOption(String value, String label, List<PermissionOption> permissions) { }
    public record PermissionOption(String code, String label) { }
}
