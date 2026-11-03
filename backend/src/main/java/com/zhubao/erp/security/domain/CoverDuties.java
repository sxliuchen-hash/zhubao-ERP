package com.zhubao.erp.security.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 业务管理账号可单独勾选的顶岗范围。数据范围仍由账号上的 dataScope 决定。 */
public final class CoverDuties {
    public static final String INTAKE = "INTAKE";
    public static final String REPORT = "REPORT";
    public static final String QUALITY = "QUALITY";
    public static final String SPLIT = "SPLIT";
    public static final String LABEL = "LABEL";
    public static final String MASTER = "MASTER";
    public static final String SHIP = "SHIP";
    public static final String RETURN = "RETURN";
    public static final String REGISTER = "REGISTER";
    public static final String LOSS = "LOSS";
    /** 客户寄存收货、领用建单和退回备料。默认不勾。 */
    public static final String RECEIVER = "RECEIVER";
    /** 收货员的寄存动作，再加上公司料、退还客户和冻结。默认不勾。 */
    public static final String WAREHOUSE = "WAREHOUSE";
    /** 打样页面。默认不勾，任何角色都可以单独授予。 */
    public static final String SAMPLING = "SAMPLING";
    /** 金工页面。默认不勾，任何角色都可以单独授予。 */
    public static final String GOLDWORK = "GOLDWORK";
    private static final List<String> PAGE_GRANTS = List.of(SAMPLING, GOLDWORK);
    private static final List<String> ORDER = List.of(
            INTAKE, REPORT, QUALITY, SPLIT, LABEL, MASTER, SHIP, RETURN, REGISTER, LOSS, RECEIVER, WAREHOUSE,
            SAMPLING, GOLDWORK);
    private static final List<String> DEFAULTS = List.of(INTAKE, REPORT, QUALITY, SPLIT, LABEL, MASTER);

    private CoverDuties() {
    }

    public static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String code = item.trim().toUpperCase(Locale.ROOT);
            if (!ORDER.contains(code)) {
                throw new BusinessException("未知顶岗权限：" + item);
            }
            found.add(code);
        }
        List<String> ordered = new ArrayList<>();
        for (String code : ORDER) {
            if (found.contains(code)) {
                ordered.add(code);
            }
        }
        return List.copyOf(ordered);
    }

    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return normalize(List.of(raw.split("[,|，、\\s]+")));
    }

    public static String join(List<String> duties) {
        return String.join(",", normalize(duties));
    }

    public static List<String> packageDefaults() {
        return DEFAULTS;
    }

    /** 非业务管理账号只保留打样、金工这两项可配置页面，不带走收货、报工等顶岗。 */
    public static List<String> pageGrants(List<String> raw) {
        List<String> normalized = raw == null ? List.of() : normalize(raw);
        List<String> pages = new ArrayList<>();
        for (String code : PAGE_GRANTS) {
            if (normalized.contains(code)) {
                pages.add(code);
            }
        }
        return List.copyOf(pages);
    }

    public static boolean contains(List<String> duties, String code) {
        return duties != null && duties.contains(code);
    }
}
