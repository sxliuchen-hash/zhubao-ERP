package com.zhubao.erp.sampling.domain;

import java.math.BigDecimal;

/**
 * 打样领用查询。实际投入未登记时为空。权属来自原库存，不因本记录改写。
 */
public record SamplingMaterialUseView(
        String useId,
        String samplingCode,
        String owner,
        String sourceId,
        Integer sourceLineNo,
        String stockMoveId,
        String materialCode,
        String batchCode,
        BigDecimal quantity,
        String unit,
        String weightState,
        BigDecimal weightValue,
        BigDecimal returnedQuantity,
        BigDecimal inputQuantity,
        long version
) {
}
