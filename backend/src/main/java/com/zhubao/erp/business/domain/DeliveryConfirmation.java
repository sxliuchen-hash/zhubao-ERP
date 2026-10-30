package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 一次已确认的交付。来源行各自对应出库流水，草稿头本身没有第二笔库存。
 */
public record DeliveryConfirmation(
        DeliveryDraft draft,
        List<DeliveryDraftLine> lines,
        List<DeliveryConfirmedSource> sources
) {
}
