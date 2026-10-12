package com.zhubao.erp.business.app;

/**
 * 质检事实保存前的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface QualityFactSaveProbe {

    QualityFactSaveProbe NONE = () -> {
    };

    void beforeFactSave();
}
