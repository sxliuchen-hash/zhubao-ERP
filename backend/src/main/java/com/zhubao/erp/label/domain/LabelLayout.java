package com.zhubao.erp.label.domain;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.zhubao.erp.common.error.BusinessException;

/** 标签版式参数，仅允许预设模式和有界数值，不接收自由样式。 */
public record LabelLayout(
        String mode,
        Double qrWidthMm,
        Double codeFontPt,
        Double fieldFontPt,
        Double emphasisFontPt,
        Double titleFontPt,
        Double footerFontPt
) {
    public LabelLayout {
        mode = mode == null || mode.isBlank() ? "AUTO" : mode.trim();
        if (!mode.equals("AUTO") && !mode.equals("COMPACT") && !mode.equals("STANDARD")) {
            throw new BusinessException("不支持的标签版式模式：" + mode);
        }
        check("二维码宽度", qrWidthMm, 5, 100);
        check("单号字号", codeFontPt, 4, 36);
        check("字段字号", fieldFontPt, 4, 36);
        check("强调字号", emphasisFontPt, 4, 36);
        check("标题字号", titleFontPt, 4, 36);
        check("脚注字号", footerFontPt, 4, 36);
    }

    public static LabelLayout defaultLayout() {
        return new LabelLayout("AUTO", null, null, null, null, null, null);
    }

    @JsonAnySetter
    public void rejectUnknownSetting(String name, Object value) {
        throw new BusinessException("不支持的标签版式参数：" + name);
    }

    private static void check(String name, Double value, double minimum, double maximum) {
        if (value != null && (!Double.isFinite(value) || value < minimum || value > maximum)) {
            throw new BusinessException(name + "超出允许范围（" + minimum + "～" + maximum + "）");
        }
    }
}
