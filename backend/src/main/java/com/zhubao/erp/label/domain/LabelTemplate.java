package com.zhubao.erp.label.domain;

import java.util.List;

/**
 * 标签模板主数据：用户可自定义标签的品牌抬头、字段列表、脚注和打印机设置。
 *
 * <p>{@code templateCode} 是模板业务编码（同类型下唯一，如 MOTHER-DEFAULT）；
 * {@code labelType} 指定该模板适用的标签类型；{@code isDefault} 标记该类型的默认模板，
 * 生成标签时若不指定模板编码则采用对应类型的默认模板。</p>
 */
public record LabelTemplate(
        Long id,
        String templateCode,
        String templateName,
        LabelType labelType,
        String brandTitle,
        List<LabelTemplateField> fields,
        String footerLeftSource,
        String footerRightSource,
        boolean isDefault,
        boolean enabled,
        LabelPrinterSetting printerSetting,
        String remark,
        String createdBy,
        String createdAt
) {
    public LabelTemplate {
        fields = fields == null ? List.of() : List.copyOf(fields);
        printerSetting = printerSetting == null ? LabelPrinterSetting.defaultSetting() : printerSetting;
    }
}
