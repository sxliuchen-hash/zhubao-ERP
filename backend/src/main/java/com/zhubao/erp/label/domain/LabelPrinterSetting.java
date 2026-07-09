package com.zhubao.erp.label.domain;

/**
 * 标签打印机设置。随标签模板一起维护，前端据此设置打印尺寸、份数并提示目标打印机。
 *
 * <p>说明：浏览器端无法静默选择系统打印机，{@code printerName} 仅作为界面提示/记录，
 * 实际打印仍走浏览器打印对话框；尺寸与份数用于生成打印样式与默认份数。</p>
 */
public record LabelPrinterSetting(
        int widthMm,
        int heightMm,
        int dpi,
        String printerName,
        int copies
) {
    public LabelPrinterSetting {
        widthMm = widthMm <= 0 ? 60 : widthMm;
        heightMm = heightMm <= 0 ? 40 : heightMm;
        dpi = dpi <= 0 ? 203 : dpi;
        printerName = printerName == null ? "" : printerName.trim();
        copies = copies <= 0 ? 1 : copies;
    }

    /** 默认标签打印设置：60×40mm、203dpi、未指定打印机、1 份。 */
    public static LabelPrinterSetting defaultSetting() {
        return new LabelPrinterSetting(60, 40, 203, "", 1);
    }
}
