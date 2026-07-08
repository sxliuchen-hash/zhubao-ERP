package com.zhubao.erp.register.domain;

public record ConfirmRegisterImportCommand(
        RegisterImportPreview preview,
        String operator,
        String confirmRemark
) {
}
