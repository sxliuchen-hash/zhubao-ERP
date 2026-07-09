package com.zhubao.erp.label.domain;

/**
 * 标签模板中的一个字段定义。用户可自定义标签上展示哪些字段、字段名与取值来源。
 *
 * <p>{@code source} 为取值来源键（如 customer/goodsName/quantity/weight/inboundDate 等），
 * 用于从业务数据（如结构化母单）自动带出；来源为空或 {@code manual} 时表示人工填写。
 * {@code label} 为标签上显示的字段名（中文），{@code sortOrder} 决定展示顺序。</p>
 */
public record LabelTemplateField(
        String label,
        String source,
        int sortOrder
) {
    public LabelTemplateField {
        label = label == null ? "" : label.trim();
        source = source == null ? "" : source.trim();
    }
}
