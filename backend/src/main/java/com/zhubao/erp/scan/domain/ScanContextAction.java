package com.zhubao.erp.scan.domain;

/**
 * 固定动作是否可执行。blockKind 为 PERMISSION 或 STATE，可执行时为 NONE。
 * 动作码不是可调用的接口地址。
 */
public record ScanContextAction(
        String code,
        boolean allowed,
        String blockKind,
        String blockReason
) {
}
