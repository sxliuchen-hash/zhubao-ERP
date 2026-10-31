package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;

/**
 * 打样领用只引用真实库存移动。这里不保存另一套实物余额。
 * 尚未登记的实际投入保持为空，不写成 0。
 */
public record SamplingMaterialUse(
        String useId,
        String samplingCode,
        OwnerType owner,
        String sourceId,
        Integer sourceLineNo,
        String stockMoveId,
        String materialCode,
        String batchCode,
        BigDecimal quantity,
        String unit,
        StockMeasurement.Weight weight,
        BigDecimal returnedQuantity,
        BigDecimal inputQuantity,
        long version
) {
    public SamplingMaterialUse {
        if (useId == null || useId.isBlank()) {
            throw new BusinessException("领用编号无效");
        }
        useId = useId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(samplingCode);
        if (reference.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("领用必须挂在打样单上");
        }
        samplingCode = reference.code();
        if (owner != OwnerType.CUSTOMER && owner != OwnerType.COMPANY) {
            throw new BusinessException("领用权属无效");
        }
        sourceId = required(sourceId, "请选择真实来源");
        stockMoveId = required(stockMoveId, "缺少库存移动");
        materialCode = required(materialCode, "请选择物料");
        batchCode = required(batchCode, "请选择批次");
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("领用数量必须大于零");
        }
        unit = required(unit, "请填写单位");
        if (weight == null) {
            throw new BusinessException("缺少重量状态");
        }
        if (weight.state() == StockMeasurement.WeightState.UNKNOWN && weight.value() != null) {
            throw new BusinessException("未知重量不能写成数值");
        }
        if (weight.state() == StockMeasurement.WeightState.KNOWN
                && (weight.value() == null || weight.value().signum() <= 0)) {
            throw new BusinessException("未知重量不能写成 0");
        }
        if (returnedQuantity == null || returnedQuantity.signum() < 0) {
            throw new BusinessException("退回数量无效");
        }
        if (inputQuantity != null && inputQuantity.signum() <= 0) {
            throw new BusinessException("实际投入必须大于零");
        }
        BigDecimal used = inputQuantity == null ? BigDecimal.ZERO : inputQuantity;
        if (returnedQuantity.add(used).compareTo(quantity) > 0) {
            throw new BusinessException("退回和投入不能超过领用");
        }
        if (version < 1) {
            throw new BusinessException("领用版本无效");
        }
    }

    public SamplingMaterialUse returnMore(BigDecimal more) {
        if (more == null || more.signum() <= 0) {
            throw new BusinessException("退回数量必须大于零");
        }
        return new SamplingMaterialUse(useId, samplingCode, owner, sourceId, sourceLineNo, stockMoveId, materialCode,
                batchCode, quantity, unit, weight, returnedQuantity.add(more), inputQuantity, version + 1);
    }

    public SamplingMaterialUse recordInput(BigDecimal input) {
        if (inputQuantity != null) {
            throw new BusinessException("实际投入已经登记");
        }
        return new SamplingMaterialUse(useId, samplingCode, owner, sourceId, sourceLineNo, stockMoveId, materialCode,
                batchCode, quantity, unit, weight, returnedQuantity, input, version + 1);
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }
}
