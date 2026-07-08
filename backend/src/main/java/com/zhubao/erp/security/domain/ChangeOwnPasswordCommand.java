package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

public record ChangeOwnPasswordCommand(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}
