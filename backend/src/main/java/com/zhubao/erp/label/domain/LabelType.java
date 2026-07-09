package com.zhubao.erp.label.domain;

/**
 * 标签类型。二维码内容采用系统统一扫码格式 {@code JG|<scanType>|<编码>}，与前端 scanCode 解析一致。
 */
public enum LabelType {

    /** 母单标签（贴周转盒/周转袋）。 */
    MOTHER("母单", "mother"),

    /** 子工单标签（工人扫码报工）。 */
    WORK_ORDER("子工单", "work_order"),

    /** 出货标签（贴快递包裹）。 */
    OUTBOUND("出货", "outbound"),

    /** 返工标签（跟随返工品）。 */
    REWORK("返工", "rework");

    private final String label;
    private final String scanType;

    LabelType(String label, String scanType) {
        this.label = label;
        this.scanType = scanType;
    }

    public String label() {
        return label;
    }

    /** 扫码内容中的类型段（如 mother/work_order/outbound/rework）。 */
    public String scanType() {
        return scanType;
    }

    public static LabelType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LabelType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
