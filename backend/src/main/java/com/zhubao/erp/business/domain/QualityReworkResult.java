package com.zhubao.erp.business.domain;

/**
 * 返工派发结果。返工单关联原单和这次质检。
 */
public record QualityReworkResult(
        String reworkOrderCode,
        String qualityCaseCode,
        String orderCode,
        String motherOrderCode,
        String ledgerCode,
        String fromStockStatus,
        String toStockStatus
) {
}
