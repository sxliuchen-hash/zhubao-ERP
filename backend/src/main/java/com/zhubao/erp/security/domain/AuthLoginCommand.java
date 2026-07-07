package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginCommand(
        @NotBlank String username,
        @NotBlank String password
) {
}
