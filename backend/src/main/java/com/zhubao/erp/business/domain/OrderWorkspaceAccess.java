package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 会话已经算好的菜单和工序绑定。查询不能再相信客户端传来的角色。
 */
public record OrderWorkspaceAccess(List<String> menuCodes, List<String> allowedProcesses) {
    public OrderWorkspaceAccess {
        menuCodes = menuCodes == null ? List.of() : List.copyOf(menuCodes);
        allowedProcesses = allowedProcesses == null ? List.of() : List.copyOf(allowedProcesses);
    }
}
