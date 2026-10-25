package com.zhubao.erp.business.domain;

import java.util.List;

/** 加工意图的待核资料，不是库存单据；未称重量可为空。 */
public record ProcessingIntakeDraft(Long customerId, String customerName, String sourceType, String courierNo,
                                    String processingRequirement, String dueDate, List<Line> lines,
                                    String requirementTemplateCode, Integer requirementTemplateVersion, String localDraftKey) {
    public ProcessingIntakeDraft { lines = lines == null ? List.of() : List.copyOf(lines); }
    public record Line(String materialCode, String goodsName, String quantity, String quantityUnit, String weight) { }
}
