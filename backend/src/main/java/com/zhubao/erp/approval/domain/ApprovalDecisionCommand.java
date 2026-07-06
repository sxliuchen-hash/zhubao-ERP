package com.zhubao.erp.approval.domain;

import jakarta.validation.constraints.NotBlank;

public record ApprovalDecisionCommand(
        @NotBlank String decision,
        @NotBlank String operator,
        String comment,
        String evidenceCode
) {
}
