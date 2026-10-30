package com.zhubao.erp.inventory.app;

/**
 * 客户库存按母单读取数量单位。不同单位不能加在一起。
 */
public interface CustomerStockUnitSource {

    String unitOf(String motherOrderCode);
}
