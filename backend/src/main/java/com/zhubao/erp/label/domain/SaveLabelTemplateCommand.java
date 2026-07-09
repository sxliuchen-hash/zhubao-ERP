package com.zhubao.erp.label.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 新增或更新标签模板的命令。{@code id} 为空表示新增，非空表示更新。
 * 受 {@code LABEL_PRINT} 权限守卫（模板维护复用打印权限）。
 */
public record SaveLabelTemplateCommand(
        Long id,
        @NotBlank String templateCode,
        @NotBlank String templateName,
        @NotBlank String labelType,
        String brandTitle,
        List<LabelTemplateField> fields,
        String footerLeftSource,
        String footerRightSource,
        Boolean isDefault,
        Boolean enabled,
        Integer widthMm,
        Integer heightMm,
        Integer dpi,
        String printerName,
        Integer copies,
        String remark,
        String operator
) {
}
