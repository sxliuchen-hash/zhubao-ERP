package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 现场工序。顺序固定为雕刻、镶嵌、抛光。账号可绑定其中一道或多道，扫码报工按单上记下的列表依次交付。
 */
public final class WorkshopProcesses {

    public static final List<String> DEFAULT_ORDER = List.of("雕刻", "镶嵌", "抛光");

    private WorkshopProcesses() {
    }

    /**
     * 按默认顺序保留勾选的工序。传入 null 表示沿用全部默认工序；空列表表示全部勾掉。
     */
    public static List<String> normalize(List<String> selected) {
        if (selected == null) {
            return DEFAULT_ORDER;
        }
        if (selected.isEmpty()) {
            throw new BusinessException("至少选择一道工序");
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (String name : selected) {
            String trimmed = name == null ? "" : name.trim();
            if (!DEFAULT_ORDER.contains(trimmed)) {
                throw new BusinessException("未知工序：" + trimmed);
            }
            chosen.add(trimmed);
        }
        List<String> ordered = new ArrayList<>();
        for (String name : DEFAULT_ORDER) {
            if (chosen.contains(name)) {
                ordered.add(name);
            }
        }
        return List.copyOf(ordered);
    }

    /**
     * 账号可做的工序。空列表表示不绑定；未知名称拒绝。结果按默认顺序排列。
     */
    public static List<String> bind(List<String> selected) {
        if (selected == null || selected.isEmpty()) {
            return List.of();
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (String name : selected) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!DEFAULT_ORDER.contains(trimmed)) {
                throw new BusinessException("未知工序：" + trimmed);
            }
            chosen.add(trimmed);
        }
        if (chosen.isEmpty()) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>();
        for (String name : DEFAULT_ORDER) {
            if (chosen.contains(name)) {
                ordered.add(name);
            }
        }
        return List.copyOf(ordered);
    }

    public static String join(List<String> processes) {
        return String.join("、", processes == null ? List.of() : processes);
    }

    public static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.copyOf(List.of(stored.split("、")));
    }
}
