package com.zhubao.erp.finance.app;

/**
 * 旧耗材数量写入口。接续完成后拒绝再写旧账。
 */
public interface ConsumableQuantityWriteGate {

    void assertWritable(String itemCode);
}
