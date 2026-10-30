package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.common.error.BusinessException;

/**
 * 把已确认的类别规则落到档案上。规格只保存调用方给出的身份，不拆属性、不换算。
 */
public final class FactoryItemPolicy {

    private FactoryItemPolicy() {
    }

    public static ConsumptionTiming timing(FactoryItemCategory category, ConsumptionTiming requested) {
        if (category == null) {
            throw new BusinessException("请选择管理类别");
        }
        if (category == FactoryItemCategory.REUSABLE_TOOL) {
            if (requested == ConsumptionTiming.ON_ISSUE || requested == ConsumptionTiming.ON_ACTUAL_USE) {
                throw new BusinessException("重复工具借用不是耗用");
            }
            return ConsumptionTiming.UNDECIDED;
        }
        if (category == FactoryItemCategory.BEAD || category == FactoryItemCategory.ACCESSORY) {
            if (requested == ConsumptionTiming.ON_ISSUE) {
                throw new BusinessException("公司珠料和配件的领用不是消耗");
            }
            return ConsumptionTiming.ON_ACTUAL_USE;
        }
        if (requested == null || requested == ConsumptionTiming.UNDECIDED) {
            return ConsumptionTiming.UNDECIDED;
        }
        return requested;
    }

    public static CustodyMode custody(FactoryItemCategory category, CustodyMode requested) {
        if (category == FactoryItemCategory.REUSABLE_TOOL) {
            if (requested == null) {
                return CustodyMode.UNDECIDED;
            }
            return requested;
        }
        if (requested == CustodyMode.QUANTITY || requested == CustodyMode.PIECE) {
            throw new BusinessException("数量或逐件保管只用于重复工具");
        }
        return CustodyMode.UNDECIDED;
    }

    /**
     * 可独立领用的身份。材质、规格和单位都算进去，同材质的不同规格不会合成一条。
     */
    public static String identity(FactoryItemCategory category, String materialName, String specCode, String unit) {
        return category.name() + "|" + materialName + "|" + specCode + "|" + unit;
    }
}
