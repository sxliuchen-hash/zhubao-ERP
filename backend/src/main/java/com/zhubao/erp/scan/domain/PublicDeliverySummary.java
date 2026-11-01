package com.zhubao.erp.scan.domain;

import java.math.BigDecimal;
import java.util.List;

/** 持码可见的确认快照。不含工资、成本、欠款和内部文件地址。 */
public record PublicDeliverySummary(String accessId, String deliveryId, long customerId,
        List<String> bagCodes, List<String> publicPhotoIds, String customerName, List<Bag> bags, String remark) {
    public record Bag(String bagCode, String material, String specification, String requirement,
                      BigDecimal quantity, String unit, BigDecimal weight, String sourceCode) {}
}
