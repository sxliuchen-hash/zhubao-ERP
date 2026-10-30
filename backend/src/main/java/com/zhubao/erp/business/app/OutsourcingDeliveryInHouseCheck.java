package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.DeliveryInHouseCheck;
import org.springframework.stereotype.Component;

/**
 * 用外协保管判断所选袋是否仍在本厂。查询不写发外或收回。
 */
@Component
public class OutsourcingDeliveryInHouseCheck implements DeliveryInHouseCheck {

    private final OutsourcingDispatchService dispatches;

    public OutsourcingDeliveryInHouseCheck(OutsourcingDispatchService dispatches) {
        this.dispatches = dispatches;
    }

    @Override
    public boolean inHouse(String bagId) {
        return OutsourcingDispatchService.IN_HOUSE.equals(dispatches.holderOf(bagId));
    }
}
