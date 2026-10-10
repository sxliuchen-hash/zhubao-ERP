package com.zhubao.erp.ops.app;

/**
 * 升级被拒绝或执行失败。调用方应保留消息中的恢复说明。
 */
public class UpgradeRejectedException extends RuntimeException {

    public UpgradeRejectedException(String message) {
        super(message);
    }

    public UpgradeRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
