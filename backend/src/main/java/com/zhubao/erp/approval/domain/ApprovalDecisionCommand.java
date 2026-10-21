package com.zhubao.erp.approval.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 决策命令。{@code operator} 仅兼容旧客户端，服务端以登录会话为准，不采用该字段。
 */
public record ApprovalDecisionCommand(
        @NotBlank String decision,
        String operator,
        String comment,
        String evidenceCode
) {
}
