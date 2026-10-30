package com.zhubao.erp.business.domain;

/**
 * 所选袋当前是否在本厂。外协未回的份额不能当作厂内可发来源。
 */
public interface DeliveryInHouseCheck {

    boolean inHouse(String bagId);
}
