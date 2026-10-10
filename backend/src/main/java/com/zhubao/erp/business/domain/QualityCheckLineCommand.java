package com.zhubao.erp.business.domain;

/**
 * 提交时某一条的判定。条款正文以单据上的加工要求为准，不采用请求里改写的文字。
 */
public record QualityCheckLineCommand(
        Integer lineNo,
        String verdict,
        String reason
) {
}
