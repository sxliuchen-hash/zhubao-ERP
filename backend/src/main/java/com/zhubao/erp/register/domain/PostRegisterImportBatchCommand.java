package com.zhubao.erp.register.domain;

public record PostRegisterImportBatchCommand(
        Boolean allowWarnings,
        String operator,
        String postingRemark
) {
}
