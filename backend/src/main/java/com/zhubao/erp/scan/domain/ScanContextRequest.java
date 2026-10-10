package com.zhubao.erp.scan.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 统一扫码上下文查询。objectType 和 intent 都可以省略。
 */
public record ScanContextRequest(
        @NotBlank String content,
        ScanObjectType objectType,
        ScanIntent intent
) {
}
