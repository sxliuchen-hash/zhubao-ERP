package com.zhubao.erp.business.domain;

/**
 * 正常整袋报工的一次提交。进度可以不填，不能靠 100% 代替本次数量和照片。
 */
public record WholeBagReportCommand(
        String requestId,
        String bagId,
        int bagVersion,
        String expectedStepId,
        QuantityInput quantity,
        String attachmentCode,
        Integer progressPercent
) {
}
