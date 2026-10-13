package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

public record ChangeOwnPasswordCommand(
        @NotBlank(message = "请输入当前密码") String currentPassword,
        @NotBlank(message = "请输入新密码") String newPassword
) {
}
