package com.zhubao.erp.scan.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 快递运单号识别。面单二维码通常是运单号本身，或带运单号的网址，不是系统标签的 JG| 格式。
 */
public final class CourierNumbers {

    private static final Pattern QUERY_WAYBILL = Pattern.compile(
            "(?i)(?:waybillNo|mailNo|billNo|expressNo|logisticCode|waybill)=([A-Za-z0-9-]{8,32})"
    );

    private CourierNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isWaybill(String normalized) {
        if (normalized == null || normalized.length() < 8 || normalized.length() > 32) {
            return false;
        }
        if (!normalized.matches("[A-Z0-9-]+") || normalized.startsWith("-")) {
            return false;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isDigit(normalized.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从面单原文里取出运单号。系统标签 JG| 不在这里处理。
     */
    public static String extract(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            Matcher matcher = QUERY_WAYBILL.matcher(trimmed);
            if (matcher.find() && isWaybill(normalize(matcher.group(1)))) {
                return normalize(matcher.group(1));
            }
            int slash = trimmed.lastIndexOf('/');
            String tail = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
            int query = tail.indexOf('?');
            if (query >= 0) {
                tail = tail.substring(0, query);
            }
            String normalizedTail = normalize(tail);
            return isWaybill(normalizedTail) ? normalizedTail : "";
        }
        String normalized = normalize(trimmed);
        return isWaybill(normalized) ? normalized : "";
    }
}
