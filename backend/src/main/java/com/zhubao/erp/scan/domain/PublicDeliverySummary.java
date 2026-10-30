package com.zhubao.erp.scan.domain;

import java.util.List;

/**
 * 持码可见的交付摘要。不含工资、成本、欠款和内部文件地址。
 */
public record PublicDeliverySummary(
        String accessId,
        String deliveryId,
        long customerId,
        List<String> bagCodes,
        List<String> publicPhotoIds
) {
}
