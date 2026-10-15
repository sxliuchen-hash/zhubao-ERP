package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * 业务单号：订单类型、年月日、四位当日流水。
 * 同一天已经用过的三位旧流水计入序号，避免改成四位后和旧单撞号。
 */
public final class OrderCodes {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private OrderCodes() {
    }

    public static String next(String type, LocalDate day, Collection<String> existing, Predicate<String> taken) {
        String prefix = type + "-" + day.format(DAY) + "-";
        int serial = 0;
        for (String code : existing) {
            serial = Math.max(serial, serialOf(prefix, code));
        }
        for (int attempt = 0; attempt < 100 && serial < 9999; attempt++) {
            serial++;
            String code = prefix + String.format("%04d", serial);
            if (!taken.test(code)) {
                return code;
            }
        }
        throw new BusinessException("当天单号已用完，请稍后重试");
    }

    /** 只认本类型、本日期下的三到四位纯数字流水，其它单号不占序号。 */
    static int serialOf(String prefix, String code) {
        if (code == null || !code.startsWith(prefix)) {
            return 0;
        }
        String suffix = code.substring(prefix.length());
        if (!suffix.matches("\\d{3,4}")) {
            return 0;
        }
        return Integer.parseInt(suffix);
    }
}
