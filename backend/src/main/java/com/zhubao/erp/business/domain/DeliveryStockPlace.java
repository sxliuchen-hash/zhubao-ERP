package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.OwnerType;

/**
 * 普通加工目标上已经存在的成品库存位置。打样和金工不从母单冒充。
 */
public record DeliveryStockPlace(
        String motherOrderCode,
        String workOrderCode,
        long customerId,
        long ownerCustomerId,
        OwnerType ownerType,
        String warehouseCode,
        String materialCode
) {
}
