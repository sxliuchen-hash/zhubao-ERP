package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.PricingMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 财务金额、月份和单号的共用换算。
 */
final class FinanceTexts {

    static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<String> PROCESSES = Set.of("雕刻", "镶嵌", "抛光");

    private FinanceTexts() {
    }

    static String now() {
        return TIME.format(LocalDateTime.now());
    }

    static String code(String prefix, AtomicLong sequence) {
        return prefix + "-" + System.currentTimeMillis() + "-" + sequence.incrementAndGet();
    }

    static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    static YearMonth month(String value) {
        String text = blank(value);
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
    }

    static LocalDate day(String value) {
        String text = blank(value);
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("日期格式应为 YYYY-MM-DD");
        }
    }

    static boolean covers(String from, String to, YearMonth month) {
        YearMonth start = YearMonth.parse(from);
        if (month.isBefore(start)) {
            return false;
        }
        if (blank(to).isEmpty()) {
            return true;
        }
        return !month.isAfter(YearMonth.parse(to));
    }

    static boolean overlaps(String leftFrom, String leftTo, YearMonth rightFrom, YearMonth rightTo) {
        YearMonth start = YearMonth.parse(leftFrom);
        YearMonth end = blank(leftTo).isEmpty() ? YearMonth.of(9999, 12) : YearMonth.parse(leftTo);
        YearMonth newEnd = rightTo == null ? YearMonth.of(9999, 12) : rightTo;
        return !rightFrom.isAfter(end) && !start.isAfter(newEnd);
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal price(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    static PricingMode pricingMode(String raw) {
        try {
            return PricingMode.valueOf(blank(raw).toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("计价方式只能是按克、按件或按工序");
        }
    }

    static ChargeUnit chargeUnit(String raw) {
        try {
            return ChargeUnit.valueOf(blank(raw).toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("计价单位只能是克、件或次");
        }
    }

    static void requireProcess(String processName) {
        if (!PROCESSES.contains(blank(processName))) {
            throw new BusinessException("工序只能是雕刻、镶嵌或抛光");
        }
    }

    static String monthOfTimestamp(String timestamp) {
        String text = blank(timestamp);
        if (text.length() >= 7 && text.charAt(4) == '-') {
            return text.substring(0, 7);
        }
        return "";
    }

    static int depreciationIndex(String startMonth, String yearMonth) {
        return (int) ChronoUnit.MONTHS.between(YearMonth.parse(startMonth), YearMonth.parse(yearMonth)) + 1;
    }

    static BigDecimal summaryAmount(String summary, String key) {
        String token = key + "=";
        if (summary == null || !summary.contains(token)) {
            return ZERO;
        }
        int start = summary.indexOf(token) + token.length();
        int end = summary.indexOf(';', start);
        String raw = end < 0 ? summary.substring(start) : summary.substring(start, end);
        try {
            return money(new BigDecimal(raw.trim()));
        } catch (NumberFormatException ex) {
            return ZERO;
        }
    }
}
