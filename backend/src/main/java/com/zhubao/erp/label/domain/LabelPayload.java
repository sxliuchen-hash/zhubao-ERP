package com.zhubao.erp.label.domain;

import java.util.List;

/**
 * 可打印标签的数据载荷。前端据此渲染标签并生成二维码。
 *
 * <p>设计原则：标签只放固定不变的信息（编码、客户、物料、数量、重量、日期等），
 * 加工进度等动态信息不进标签，由扫码后实时查看。</p>
 */
public record LabelPayload(
        LabelType type,
        String typeLabel,
        String primaryCode,
        String qrContent,
        List<LabelField> fields,
        String footerLeft,
        String footerRight,
        String printedAt
) {
    public LabelPayload {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
