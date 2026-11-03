package com.zhubao.erp.business.domain;

/**
 * 所选袋当前是否在本厂。外协必须先收回本厂，不能直接发给客户，也不能在外协之间转送。
 */
public interface DeliveryInHouseCheck {

    boolean inHouse(String bagId);
}
