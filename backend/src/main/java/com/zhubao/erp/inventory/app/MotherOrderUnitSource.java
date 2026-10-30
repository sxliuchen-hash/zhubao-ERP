package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import org.springframework.stereotype.Component;

/**
 * 母单上的数量单位。查不到时留空，不默认成颗。
 */
@Component
public class MotherOrderUnitSource implements CustomerStockUnitSource {

    private final BusinessDocumentRepository documents;

    public MotherOrderUnitSource(BusinessDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public String unitOf(String motherOrderCode) {
        if (motherOrderCode == null || motherOrderCode.isBlank()) {
            return "";
        }
        return documents.findMotherOrderByCode(motherOrderCode)
                .map(order -> order.quantityUnit() == null ? "" : order.quantityUnit().trim())
                .orElse("");
    }
}
