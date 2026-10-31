package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交付更正前的只读影响。交接状态保持待核，查询不写出库、库存或费用。
 */
public record DeliveryImpact(
        String deliveryId,
        DeliveryDraftStatus status,
        String handover,
        List<SourceImpact> sources,
        String confirmedChargeRecord,
        String confirmedRefundRecord
) {
    public static final String HANDOVER_UNDECIDED = "待核";

    public record SourceImpact(String sourceCode, BigDecimal originalQuantity, BigDecimal consumedQuantity) {
    }
}
