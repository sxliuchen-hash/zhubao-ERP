package com.zhubao.erp.label.domain;

/**
 * 标签上的一个信息字段（键值对）。标签只承载固定不变的信息。
 */
public record LabelField(
        String name,
        String value
) {
}
