package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.time.LocalDate;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 三类业务的有限引用。序列化形式为「种类|角色|单号」。
 * 打样号和金工号不会被当成普通母单。
 */
public record BusinessTargetReference(BusinessKind kind, BusinessTargetRole role, String code) {

    private static final Pattern CODE = Pattern.compile("^(MD|ZD|DY|GJ)-(\\d{8})-(\\d{3,4})$");

    public BusinessTargetReference {
        if (kind == null || role == null) {
            throw new BusinessException("业务引用缺少种类或角色");
        }
        code = code == null ? "" : code.trim();
        if (code.isEmpty()) {
            throw new BusinessException("业务引用缺少单号");
        }
        if (kind == BusinessKind.PROCESSING && role == BusinessTargetRole.DOCUMENT) {
            throw new BusinessException("普通加工引用使用母单或子单角色");
        }
        if (kind != BusinessKind.PROCESSING && role != BusinessTargetRole.DOCUMENT) {
            throw new BusinessException("打样和金工引用使用各自单据角色");
        }
    }

    public boolean processingMother() {
        return kind == BusinessKind.PROCESSING && role == BusinessTargetRole.MOTHER;
    }

    public String canonical() {
        return kind.name() + "|" + role.name() + "|" + code;
    }

    public static BusinessTargetReference parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new BusinessException("业务引用不能为空");
        }
        if (text.indexOf('|') >= 0) {
            String[] parts = text.split("\\|", -1);
            if (parts.length != 3) {
                throw new BusinessException("无法识别的业务引用");
            }
            BusinessTargetReference parsed = new BusinessTargetReference(
                    kindOf(parts[0]), roleOf(parts[1]), parts[2]);
            BusinessTargetReference fromCode = fromCode(parsed.code());
            if (parsed.kind() != fromCode.kind() || parsed.role() != fromCode.role()) {
                throw new BusinessException("业务引用种类与单号不一致");
            }
            return parsed;
        }
        return fromCode(text);
    }

    public static BusinessTargetReference fromCode(String code) {
        Matcher matcher = CODE.matcher(code == null ? "" : code.trim());
        if (!matcher.matches()) {
            throw new BusinessException("无法识别的业务单号");
        }
        return switch (matcher.group(1)) {
            case "MD" -> new BusinessTargetReference(BusinessKind.PROCESSING, BusinessTargetRole.MOTHER, matcher.group());
            case "ZD" -> new BusinessTargetReference(BusinessKind.PROCESSING, BusinessTargetRole.WORK_ORDER, matcher.group());
            case "DY" -> new BusinessTargetReference(BusinessKind.SAMPLING, BusinessTargetRole.DOCUMENT, matcher.group());
            case "GJ" -> new BusinessTargetReference(BusinessKind.GOLDWORK, BusinessTargetRole.DOCUMENT, matcher.group());
            default -> throw new BusinessException("无法识别的业务单号");
        };
    }

    public static String nextSamplingCode(LocalDate day, Collection<String> existing, Predicate<String> taken) {
        return OrderCodes.next("DY", day, existing, taken);
    }

    public static String nextGoldworkCode(LocalDate day, Collection<String> existing, Predicate<String> taken) {
        return OrderCodes.next("GJ", day, existing, taken);
    }

    private static BusinessKind kindOf(String text) {
        try {
            return BusinessKind.valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("无法识别的业务种类");
        }
    }

    private static BusinessTargetRole roleOf(String text) {
        try {
            return BusinessTargetRole.valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("无法识别的业务角色");
        }
    }
}
