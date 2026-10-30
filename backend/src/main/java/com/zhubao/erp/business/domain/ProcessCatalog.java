package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.List;

/**
 * 已确认的工序表。质检仍排在穿串之前。模板只给新单默认顺序。
 */
public final class ProcessCatalog {

    public static final ProcessStep POLISH_BEAD = step("POLISH_BEAD", "抛光（珠）", BusinessKind.PROCESSING);
    public static final ProcessStep POLISH_GOLD = step("POLISH_GOLD", "抛光（金）", BusinessKind.GOLDWORK);

    private static final List<ProcessStep> FULL = List.of(
            step("SCREEN", "筛籽选料", BusinessKind.PROCESSING),
            step("CUT", "切料", BusinessKind.PROCESSING),
            step("BLANK", "取胚", BusinessKind.PROCESSING),
            step("DRILL", "打孔", BusinessKind.PROCESSING),
            step("SHAPE", "车型", BusinessKind.PROCESSING),
            step("SELECT", "精挑", BusinessKind.PROCESSING),
            step("TRIM_EAR", "去耳", BusinessKind.PROCESSING),
            POLISH_BEAD,
            step("GROOVE", "开槽", BusinessKind.PROCESSING),
            step("INLAY_MARK", "镶标", BusinessKind.PROCESSING),
            step("REMOVE_GLUE", "除胶", BusinessKind.PROCESSING),
            step("PHOTO", "拍照", BusinessKind.PROCESSING),
            step("COUNT", "核数", BusinessKind.PROCESSING),
            step("INSPECT", "质检", BusinessKind.PROCESSING),
            step("STRING", "穿串", BusinessKind.PROCESSING)
    );

    private static final List<String> FULL_MATERIALS = List.of("牙骨", "紫金鼠", "橄榄", "小桃核", "猴头", "木料");

    private static final List<ProcessStep> SAMPLING = List.of(
            step("SAMPLE_FLAT", "平设", BusinessKind.SAMPLING),
            step("SAMPLE_MODEL", "建模", BusinessKind.SAMPLING),
            step("SAMPLE_BEAD", "珠型确认", BusinessKind.SAMPLING),
            step("SAMPLE_MAKE", "选料与加工", BusinessKind.SAMPLING),
            step("SAMPLE_DELIVER", "交付确认", BusinessKind.SAMPLING)
    );

    private static final List<ProcessStep> GOLD = List.of(
            step("GOLD_FLAT", "平设", BusinessKind.GOLDWORK),
            step("GOLD_MODEL", "建模", BusinessKind.GOLDWORK),
            step("GOLD_CAST", "倒模", BusinessKind.GOLDWORK),
            step("GOLD_FINISH", "执模", BusinessKind.GOLDWORK),
            POLISH_GOLD
    );

    private ProcessCatalog() {
    }

    public static List<ProcessStep> defaults(BusinessKind kind, String material) {
        if (kind == BusinessKind.SAMPLING) {
            return SAMPLING;
        }
        if (kind == BusinessKind.GOLDWORK) {
            return GOLD;
        }
        if (kind != BusinessKind.PROCESSING) {
            throw new BusinessException("无法给出该业务的默认路线");
        }
        String name = material == null ? "" : material.trim();
        if (FULL_MATERIALS.contains(name)) {
            return FULL;
        }
        if ("星月".equals(name)) {
            return FULL.subList(4, 15);
        }
        if ("大核桃".equals(name)) {
            return List.of(FULL.get(0), FULL.get(8), FULL.get(9), FULL.get(10), FULL.get(11), FULL.get(12), FULL.get(13), FULL.get(14));
        }
        throw new BusinessException("该材质没有已确认的默认路线");
    }

    public static ProcessStep require(String id) {
        return known().stream().filter(step -> step.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException("未知工序：" + id));
    }

    private static List<ProcessStep> known() {
        return List.of(FULL, SAMPLING, GOLD).stream().flatMap(List::stream).toList();
    }

    private static ProcessStep step(String id, String name, BusinessKind kind) {
        return new ProcessStep(id, name, kind);
    }
}
