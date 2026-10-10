package com.zhubao.erp.business.domain;

/**
 * 当前账号在这张单上可以看见的一道工序。
 */
public record ProcessStationCard(
        String processName,
        String requirement,
        String dueDate,
        String previousProcessName,
        boolean previousSubmitted,
        boolean submitted
) {
}
