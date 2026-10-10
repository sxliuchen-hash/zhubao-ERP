package com.zhubao.erp.scan.domain;

/**
 * 一条要求及其来源。来源用稳定代码，中文由展示层映射。
 */
public record ScanRequirementItem(
        String source,
        String process,
        String text
) {
}
