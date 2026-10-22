package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * 把勾选步骤收成一句加工要求。步骤之间不换行，避免被质检拆成多条任务。
 * 补充说明另起一行，是否成为质检条款由最终文本决定。
 */
public final class RequirementText {

    public static final int MAX_LENGTH = 500;
    public static final int MAX_STEP_LENGTH = 40;
    public static final int MAX_STEPS = 12;

    private RequirementText() {
    }

    public static String compose(List<String> steps, String supplement) {
        List<String> normalized = normalizeSteps(steps);
        if (normalized.isEmpty()) {
            throw new BusinessException("请至少选择一项加工步骤");
        }
        String sentence = sentence(normalized);
        String extra = supplement == null ? "" : supplement.trim();
        String text = extra.isEmpty() ? sentence : sentence + "\n" + extra;
        if (text.length() > MAX_LENGTH) {
            throw new BusinessException("加工要求超过 500 字，请缩短后再保存");
        }
        return text;
    }

    public static void requireWithinLimit(String requirement) {
        if (requirement != null && requirement.trim().length() > MAX_LENGTH) {
            throw new BusinessException("加工要求超过 500 字，请缩短后再保存");
        }
    }

    private static String sentence(List<String> steps) {
        if (steps.size() == 1) {
            return steps.get(0);
        }
        if (steps.size() == 2) {
            return "先" + steps.get(0) + "，再" + steps.get(1);
        }
        StringBuilder text = new StringBuilder("先").append(steps.get(0));
        for (int index = 1; index < steps.size() - 1; index++) {
            text.append("，再").append(steps.get(index));
        }
        text.append("，最后").append(steps.get(steps.size() - 1));
        return text.toString();
    }

    private static List<String> normalizeSteps(List<String> steps) {
        List<String> normalized = new ArrayList<>();
        if (steps == null) {
            return normalized;
        }
        for (String step : steps) {
            String phrase = step == null ? "" : step.trim();
            if (phrase.isEmpty()) {
                continue;
            }
            if (phrase.length() > MAX_STEP_LENGTH) {
                throw new BusinessException("加工步骤过长");
            }
            if (phrase.chars().anyMatch(ch -> ch == '、' || ch == ',' || ch == '，' || ch == ';' || ch == '；' || ch == '\n' || ch == '\r')) {
                throw new BusinessException("加工步骤不要包含顿号、逗号或换行");
            }
            normalized.add(phrase);
        }
        if (normalized.size() > MAX_STEPS) {
            throw new BusinessException("加工步骤过多，请合并后再保存");
        }
        return normalized;
    }
}
