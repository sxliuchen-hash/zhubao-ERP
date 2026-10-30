package com.zhubao.erp.document.domain;

/**
 * 发给客户的模型说明。外部引用默认内部，不包含存放说明。
 */
public record CustomerModelReferenceView(
        String fileName,
        String format,
        String version,
        String audience
) {
}
