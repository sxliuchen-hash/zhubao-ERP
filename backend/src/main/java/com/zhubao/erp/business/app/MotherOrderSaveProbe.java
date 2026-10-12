package com.zhubao.erp.business.app;

/**
 * 母单保存前的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface MotherOrderSaveProbe {

    MotherOrderSaveProbe NONE = () -> {
    };

    void beforeMotherSave();
}
