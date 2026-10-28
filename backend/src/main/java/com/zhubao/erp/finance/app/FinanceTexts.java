package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 财务金额、月份和单号的共用换算。
 */
final class FinanceTexts {

    static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    static int depreciationIndex(String startMonth, String yearMonth) {
        return (int) ChronoUnit.MONTHS.between(YearMonth.parse(startMonth), YearMonth.parse(yearMonth)) + 1;
    }

}
