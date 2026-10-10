package com.zhubao.erp.finance.domain;

import java.util.List;

/**
 * 客户未收排行占位。本段不查询排行，不能用空列表表示已经查过且没有余额。
 */
public record ReceivableRanking(
        int total,
        int returned,
        boolean truncated,
        List<String> rows
) {
}
