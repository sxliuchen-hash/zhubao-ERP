package com.zhubao.erp.label.domain;

import java.util.List;

/**
 * 发货袋贴纸预览。溢出只作提示，纸型保持 40×30。
 */
public record ShippingBagLabelPreview(LabelPayload payload, List<String> overflow) {
    public ShippingBagLabelPreview {
        overflow = overflow == null ? List.of() : List.copyOf(overflow);
    }
}
