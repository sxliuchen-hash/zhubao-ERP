package com.zhubao.erp.business.domain;

public record MobileIntakeLookup(
        boolean found,
        String motherOrderCode,
        String courierNo,
        String customerName,
        String processingRequirement,
        String dueDate,
        String goodsName
) {
    public static MobileIntakeLookup missing(String courierNo) {
        return new MobileIntakeLookup(false, "", courierNo, "", "", "", "");
    }
}
