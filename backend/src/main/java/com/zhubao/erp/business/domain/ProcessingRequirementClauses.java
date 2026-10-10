package com.zhubao.erp.business.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 把加工要求按换行拆成条款。空行忽略；没有换行时整段算一条。
 */
public final class ProcessingRequirementClauses {

    private ProcessingRequirementClauses() {
    }

    public static List<String> split(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return List.of();
        }
        String normalized = requirement.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.contains("\n")) {
            return List.of(normalized.trim());
        }
        List<String> clauses = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            String text = line.trim();
            if (!text.isEmpty()) {
                clauses.add(text);
            }
        }
        return List.copyOf(clauses);
    }
}
