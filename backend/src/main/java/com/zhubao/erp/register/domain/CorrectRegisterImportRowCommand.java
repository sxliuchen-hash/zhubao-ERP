package com.zhubao.erp.register.domain;

import java.util.Map;

public record CorrectRegisterImportRowCommand(
        Map<String, String> fields,
        String operator,
        String correctionRemark
) {
}
