package com.zhubao.erp.document.domain;

import java.util.Map;
import java.util.Set;

/**
 * 证据阶段、场景和单据类型的稳定代码。接口保存这些代码，中文只在展示时映射。
 */
public final class EvidenceCodes {

    public static final String STOCK_DOCUMENT = "STOCK_DOCUMENT";
    public static final String MOTHER_ORDER = "MOTHER_ORDER";
    public static final String WORK_ORDER = "WORK_ORDER";
    public static final String OUTBOUND_ORDER = "OUTBOUND_ORDER";
    public static final String REWORK = "REWORK";

    public static final String INTAKE = "INTAKE";
    public static final String PRODUCTION = "PRODUCTION";
    public static final String QUALITY = "QUALITY";
    public static final String SHIPPING = "SHIPPING";

    public static final String INBOUND_CONFIRM = "inbound_confirm";
    public static final String PROCESS_STATION_REPORT = "process_station_report";
    public static final String PRODUCTION_REPORT = "production_report";
    public static final String QUALITY_CHECK = "quality_check";
    public static final String QUALITY_FREEZE = "quality_freeze";
    public static final String QUALITY_RELEASE = "quality_release";
    public static final String REWORK_DISPATCH = "rework_dispatch";
    public static final String REWORK_REPORT = "rework_report";
    public static final String OUTBOUND_SHIPMENT = "outbound_shipment";
    public static final String PROGRESS_NOTE = "progress_note";

    public static final String LINKED = "LINKED";
    public static final String UPLOADED_PENDING_LINK = "UPLOADED_PENDING_LINK";
    public static final String HISTORICAL = "HISTORICAL";
    public static final String HISTORICAL_LABEL = "历史单据证据";

    public static final Set<String> STAGES = Set.of(INTAKE, PRODUCTION, QUALITY, SHIPPING, "REWORK");
    public static final Set<String> OBJECT_TYPES = Set.of(MOTHER_ORDER, WORK_ORDER, OUTBOUND_ORDER, REWORK, STOCK_DOCUMENT);

    private static final Map<String, String> SCENE_LABELS = Map.of(
            "UNPACK", "拆包",
            "WEIGH", "称重",
            "DEFECT", "缺陷",
            "FINISH", "成品",
            "PROCESS", "加工过程",
            "CHECK", "检查过程",
            "PACK", "打包",
            "WAYBILL", "面单"
    );

    private static final Map<String, String> CAPTURE_ALIASES = Map.of(
            "stock_document", STOCK_DOCUMENT,
            "mother", MOTHER_ORDER,
            "work_order", WORK_ORDER,
            "outbound", OUTBOUND_ORDER,
            "rework", REWORK
    );

    private EvidenceCodes() {
    }

    public static String sceneLabel(String scene) {
        if (scene == null) {
            return null;
        }
        return SCENE_LABELS.get(scene.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public static String sceneCode(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        return SCENE_LABELS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(label.trim()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    public static Set<String> scenes() {
        return SCENE_LABELS.keySet();
    }

    /**
     * 把采集接口的旧别名收成持久化单据类型。无法识别时返回空串。
     */
    public static String canonicalObjectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        if (OBJECT_TYPES.contains(text)) {
            return text;
        }
        return CAPTURE_ALIASES.getOrDefault(text, "");
    }
}
