package com.zhubao.erp.business.domain;

import java.util.List;

public record MobileIntakeLookup(
        boolean found,
        String motherOrderCode,
        String courierNo,
        String customerName,
        String processingRequirement,
        String dueDate,
        String goodsName,
        List<Candidate> candidates
) {
    public MobileIntakeLookup {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static MobileIntakeLookup missing(String courierNo) {
        return new MobileIntakeLookup(false, "", courierNo, "", "", "", "", List.of());
    }

    public record Candidate(String motherOrderCode, String customerName, String createdAt,
                            String goodsName, String processingRequirement, String dueDate) {
    }
}
