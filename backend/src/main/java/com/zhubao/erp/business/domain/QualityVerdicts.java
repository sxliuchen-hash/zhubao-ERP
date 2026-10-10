package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

/**
 * 质检对照的三种判定。不合格和让步必须写原因，合格可以不写。
 */
public final class QualityVerdicts {

    public static final String PASS = "合格";
    public static final String FAIL = "不合格";
    public static final String CONCESSION = "让步";

    private QualityVerdicts() {
    }

    public static String normalize(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new BusinessException("每一条加工要求都要有结果才能提交");
        }
        if (!PASS.equals(text) && !FAIL.equals(text) && !CONCESSION.equals(text)) {
            throw new BusinessException("判定只能是合格、不合格或让步");
        }
        return text;
    }

    public static boolean needsReason(String verdict) {
        return FAIL.equals(verdict) || CONCESSION.equals(verdict);
    }
}
