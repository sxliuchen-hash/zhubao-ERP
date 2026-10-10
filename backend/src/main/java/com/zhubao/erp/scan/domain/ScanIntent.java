package com.zhubao.erp.scan.domain;

/**
 * 扫码查询时的操作意图。意图不能代替服务端权限和状态判断。
 */
public enum ScanIntent {
    LOOKUP,
    INTAKE,
    PRODUCTION,
    QUALITY,
    SHIPPING
}
