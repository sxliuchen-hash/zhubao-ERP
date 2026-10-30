package com.zhubao.erp.business.domain;

import java.util.Optional;

/**
 * 读取加工目标的成品库存位置。找不到时返回空，不创建母单或子单。
 */
public interface DeliveryStockPlaceLookup {

    Optional<DeliveryStockPlace> place(BusinessTargetReference target);
}
