package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 一张交付草稿和它的来源明细。明细各自保留数量，不把不同单位加在一起。
 */
public record DeliveryDraftSheet(DeliveryDraft draft, List<DeliveryDraftLine> lines) {
}
