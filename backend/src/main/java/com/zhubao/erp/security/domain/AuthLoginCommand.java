package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginCommand(
        @NotBlank(message = "请输入用户名") String username,
        @NotBlank(message = "请输入密码") String password
) {
}
