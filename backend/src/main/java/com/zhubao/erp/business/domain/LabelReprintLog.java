package com.zhubao.erp.business.domain;

/**
 * 贴纸补打日志。验证码不变，只追加一次补打记录。
 */
public record LabelReprintLog(
        Long id,
        String labelType,
        String businessCode,
        String verificationCode,
        String operator,
        String reason,
        String reprintedAt
) {
}
