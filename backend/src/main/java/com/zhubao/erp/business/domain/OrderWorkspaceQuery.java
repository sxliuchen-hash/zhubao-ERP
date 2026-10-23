package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * 订单中心的只读筛选。不接收操作人、角色或客户范围，这些由会话决定。
 */
public record OrderWorkspaceQuery(
        String view,
        String query,
        Long customerId,
        String process,
        String facet,
        LocalDate dueBefore,
        int page,
        int size
) {
    public static final String ORDERS = "ORDERS";
    public static final String PRODUCTION = "PRODUCTION";
    public static final String QUALITY = "QUALITY";
    public static final String ALL = "ALL";
    public static final Set<String> VIEWS = Set.of(ORDERS, PRODUCTION, QUALITY);
    public static final Set<String> FACETS = Set.of(
            ALL, "UNSPLIT", "WAITING_START", "IN_PROCESS", "WAITING_QUALITY",
            "REWORK", "READY_TO_SHIP", "SHIPPED", "UNKNOWN"
    );

    public static OrderWorkspaceQuery parse(
            String view,
            String query,
            String customerId,
            String process,
            String facet,
            String dueBefore,
            String page,
            String size
    ) {
        String normalizedView = blank(view).isEmpty() ? ORDERS : blank(view);
        if (!VIEWS.contains(normalizedView)) {
            throw new BusinessException("不支持的订单视图");
        }
        String text = blank(query);
        if (text.length() > 100) {
            throw new BusinessException("查找内容不能超过 100 个字");
        }
        String normalizedFacet = blank(facet).isEmpty() ? ALL : blank(facet);
        if (!FACETS.contains(normalizedFacet)) {
            throw new BusinessException("不支持的订单分类");
        }
        return new OrderWorkspaceQuery(
                normalizedView,
                text,
                customerId(customerId),
                blank(process),
                normalizedFacet,
                dueDate(dueBefore),
                number(page, 1, "页码必须从 1 开始"),
                pageSize(size)
        );
    }

    private static Long customerId(String raw) {
        String text = blank(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new BusinessException("客户需使用档案编号");
        }
    }

    private static LocalDate dueDate(String raw) {
        String text = blank(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("截止日期需使用 yyyy-MM-dd");
        }
    }

    private static int number(String raw, int fallback, String message) {
        String text = blank(raw);
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(text);
            if (value < 1) {
                throw new BusinessException(message);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new BusinessException(message);
        }
    }

    private static int pageSize(String raw) {
        int value = number(raw, 20, "每页条数必须在 1 到 100 之间");
        if (value > 100) {
            throw new BusinessException("每页条数必须在 1 到 100 之间");
        }
        return value;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
