package com.zhubao.erp.label.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 生成标签的命令。前端传入标签类型、主编码和要展示的固定字段，后端组装为可打印载荷并生成二维码内容。
 */
public record GenerateLabelCommand(
        @NotBlank String type,
        @NotBlank String primaryCode,
        List<LabelField> fields,
        String footerLeft,
        String footerRight
) {
    public GenerateLabelCommand {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
