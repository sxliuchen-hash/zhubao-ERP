package com.zhubao.erp.business.domain;

/**
 * 母单选用模板时的来源。查询历史单据仍以已保存的加工要求原文为准。
 */
public record MotherRequirementSource(
        String motherOrderCode,
        String templateCode,
        int templateVersion,
        String savedRequirement
) {
}
