package com.zhubao.erp.business.domain;

/**
 * 一次真实返工报工。复检必须能指到这条记录，不能只看库存碰巧回到半成品。
 */
public record QualityReworkReport(
        String reworkOrderCode,
        String qualityCaseCode,
        String orderCode,
        String motherOrderCode,
        String ledgerCode,
        String operator,
        String createdAt
) {
}
