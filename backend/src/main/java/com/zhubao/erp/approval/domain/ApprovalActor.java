package com.zhubao.erp.approval.domain;

import com.zhubao.erp.security.domain.RoleCode;

/**
 * 审批读写使用的登录身份。操作人来自会话账号，不来自决策请求体。
 */
public record ApprovalActor(String operator, String username, RoleCode role) {

    public ApprovalActor {
        operator = operator == null ? "" : operator.trim();
        username = username == null ? "" : username.trim();
    }
}
