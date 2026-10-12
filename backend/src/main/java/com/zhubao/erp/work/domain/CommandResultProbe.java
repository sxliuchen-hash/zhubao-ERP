package com.zhubao.erp.work.domain;

/**
 * 成功结果落库前的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface CommandResultProbe {

    CommandResultProbe NONE = () -> {
    };

    void beforeResultInsert();
}
