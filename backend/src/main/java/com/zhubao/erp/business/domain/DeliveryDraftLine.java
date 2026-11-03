package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;

/**
 * 交付草稿的一袋来源。保存业务种类和真实目标号，未知重量保持未知。
 */
public record DeliveryDraftLine(
        String deliveryId,
        int lineNo,
        String bagId,
        int bagVersion,
        BusinessKind businessKind,
        BusinessTargetRole targetRole,
        String targetCode,
        BigDecimal quantity,
        String quantityUnit,
        StockMeasurement.Weight weight,
        String materialCode,
        String sourceKey,
        String requirementVersion,
        String customerName,
        String goodsSpecification,
        String processingSummary
) {
    public DeliveryDraftLine(String deliveryId, int lineNo, String bagId, int bagVersion, BusinessKind businessKind,
            BusinessTargetRole targetRole, String targetCode, BigDecimal quantity, String quantityUnit,
            StockMeasurement.Weight weight, String materialCode, String sourceKey, String requirementVersion) {
        this(deliveryId, lineNo, bagId, bagVersion, businessKind, targetRole, targetCode, quantity, quantityUnit,
                weight, materialCode, sourceKey, requirementVersion, null, null, null);
    }
    public DeliveryDraftLine {
        if (deliveryId == null || !deliveryId.matches("^FH-\\d{8}-\\d{4}$")) {
            throw new BusinessException("交付单号无效");
        }
        if (lineNo < 1) {
            throw new BusinessException("交付袋序不正确");
        }
        bagId = required(bagId, 40, "缺少交付袋", "袋号过长");
        if (bagVersion < 1) {
            throw new BusinessException("袋版本不正确");
        }
        if (businessKind == null || businessKind == BusinessKind.MIXED || targetRole == null) {
            throw new BusinessException("业务引用缺少种类或角色");
        }
        BusinessTargetReference reference = BusinessTargetReference.fromCode(targetCode);
        if (reference.kind() != businessKind || reference.role() != targetRole) {
            throw new BusinessException("业务引用种类与单号不一致");
        }
        targetCode = reference.code();
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("交付数量必须为正");
        }
        quantity = StockMeasurement.processingScale(quantity);
        quantityUnit = required(quantityUnit, 16, "数量单位不能为空", "数量单位过长");
        if (weight == null || weight.state() != StockMeasurement.WeightState.KNOWN || weight.value() == null) {
            weight = StockMeasurement.unknown();
        } else if (businessKind == BusinessKind.GOLDWORK) {
            if (weight.value().scale() > 6) {
                throw new BusinessException("金工重量最多保留六位小数，不能截断后入账");
            }
            weight = StockMeasurement.known(weight.value());
        } else {
            weight = StockMeasurement.known(StockMeasurement.processingScale(weight.value()));
        }
        materialCode = required(materialCode, 80, "所选袋缺少材质", "材质过长");
        sourceKey = sourceKey == null ? "" : sourceKey.trim();
        if (sourceKey.length() > 80) {
            throw new BusinessException("来源标识过长");
        }
        requirementVersion = requirementVersion == null ? "" : requirementVersion.trim();
        if (requirementVersion.length() > 40) {
            throw new BusinessException("加工要求版本过长");
        }
    }

    private static String required(String value, int max, String missing, String tooLong) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(missing);
        }
        if (text.length() > max) {
            throw new BusinessException(tooLong);
        }
        return text;
    }
}
