package com.zhubao.erp.business.app;

/**
 * 出货单保存前的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface OutboundSaveProbe {

    OutboundSaveProbe NONE = () -> {
    };

    void beforeOutboundSave();
}
