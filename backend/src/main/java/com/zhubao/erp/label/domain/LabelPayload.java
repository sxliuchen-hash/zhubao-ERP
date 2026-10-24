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
        String printedAt,
        String brandTitle,
        String templateCode,
        LabelPrinterSetting printerSetting,
        LabelLayout layout,
        List<StorageLabelSource> storageSources,
        int storageEdition
) {
    public LabelPayload {
        storageSources = storageSources == null ? List.of() : List.copyOf(storageSources);
        fields = fields == null ? List.of() : List.copyOf(fields);
        printerSetting = printerSetting == null ? LabelPrinterSetting.defaultSetting() : printerSetting;
        layout = layout == null ? LabelLayout.defaultLayout() : layout;
    }

    public LabelPayload withStorage(List<StorageLabelSource> sources, int edition) {
        String qr = qrContent;
        if (edition > 0) {
            String[] parts = qr.split("\\|", -1);
            if (parts.length == 5) { parts[4] = parts[4].split("-S", 2)[0] + "-S" + edition; qr = String.join("|", parts); }
            else if (parts.length >= 3 && "STOCK".equals(parts[0])) qr = String.join("|", java.util.Arrays.copyOf(parts, 3)) + "|S" + edition;
        }
        sources = sources.stream().map(source -> new StorageLabelSource(source.sourceCode(), source.sourceLabel().replaceAll("/S[0-9]+$", "") + "/S" + edition, source.materialCode(), source.recorded(), source.marks(), source.instruction())).toList();
        return new LabelPayload(type, typeLabel, primaryCode, qr, fields, footerLeft, footerRight, printedAt, brandTitle, templateCode, printerSetting, layout, sources, edition);
    }
    public LabelPayload(LabelType type, String typeLabel, String primaryCode, String qrContent,
            List<LabelField> fields, String footerLeft, String footerRight, String printedAt,
            String brandTitle, String templateCode, LabelPrinterSetting printerSetting, LabelLayout layout, List<StorageLabelSource> storageSources) {
        this(type, typeLabel, primaryCode, qrContent, fields, footerLeft, footerRight, printedAt, brandTitle, templateCode, printerSetting, layout, storageSources, 0);
    }

    /** 补打比较可见内容，打印时间变化不改变内容指纹。 */
    @com.fasterxml.jackson.annotation.JsonProperty("snapshotHash")
    public String snapshotHash() {
        try {
            byte[] content = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(List.of(type, typeLabel, primaryCode, qrContent, fields, footerLeft, footerRight, brandTitle, templateCode, printerSetting, layout, storageSources, storageEdition));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) { throw new IllegalStateException("标签内容指纹生成失败", ex); }
    }

    public double storageHeightMm() { return StorageLabelSource.height(storageSources, printerSetting.widthMm()); }

    public LabelPayload(LabelType type, String typeLabel, String primaryCode, String qrContent,
            List<LabelField> fields, String footerLeft, String footerRight, String printedAt,
            String brandTitle, String templateCode, LabelPrinterSetting printerSetting, LabelLayout layout) {
        this(type, typeLabel, primaryCode, qrContent, fields, footerLeft, footerRight, printedAt, brandTitle, templateCode, printerSetting, layout, List.of());
    }

    public LabelPayload(LabelType type, String typeLabel, String primaryCode, String qrContent,
            List<LabelField> fields, String footerLeft, String footerRight, String printedAt,
            String brandTitle, String templateCode, LabelPrinterSetting printerSetting) {
        this(type, typeLabel, primaryCode, qrContent, fields, footerLeft, footerRight,
                printedAt, brandTitle, templateCode, printerSetting, null);
    }
}
