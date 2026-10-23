package com.zhubao.erp.business.domain;

import java.util.List;
import java.util.Map;

/**
 * 订单中心的一页只读结果。totalRows 是筛选后的目标行数，不是母单数。
 */
public record OrderWorkspacePage(
        List<OrderWorkspaceRow> items,
        int page,
        int size,
        int totalRows,
        Map<String, Integer> facetCounts,
        List<String> availableViews,
        String generatedAt
) {
}
