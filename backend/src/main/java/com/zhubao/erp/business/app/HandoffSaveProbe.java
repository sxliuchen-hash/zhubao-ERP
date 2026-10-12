package com.zhubao.erp.business.app;

/**
 * 工序交付事实保存前的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface HandoffSaveProbe {

    HandoffSaveProbe NONE = () -> {
    };

    void beforeHandoffSave();
}
