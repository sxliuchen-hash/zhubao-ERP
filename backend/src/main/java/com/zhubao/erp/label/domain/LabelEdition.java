package com.zhubao.erp.label.domain;

/**
 * 从标签二维码读出的版本。同一业务单上版本不同，就不是同一版贴纸。
 */
public record LabelEdition(String businessCode, String version, String instance) {
}
