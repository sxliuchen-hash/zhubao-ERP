package com.zhubao.erp.inventory.domain;

import java.util.Set;

/**
 * 判断台账是否触及指定客户或母单。内存过滤与数据库条件共用，避免两套范围结果不一致。
 */
public final class CustomerLedgerFilter {

    private CustomerLedgerFilter() {
    }

    public static boolean touches(StockLedgerEntry entry, Set<Long> customerIds, Set<String> motherOrderCodes) {
        return touches(entry == null ? null : entry.to(), customerIds, motherOrderCodes)
                || touches(entry == null ? null : entry.from(), customerIds, motherOrderCodes);
    }

    public static boolean touches(StockDimension dimension, Set<Long> customerIds, Set<String> motherOrderCodes) {
        if (dimension == null) {
            return false;
        }
        if (dimension.customerId() != null && customerIds != null && customerIds.contains(dimension.customerId())) {
            return true;
        }
        return dimension.motherOrderCode() != null && motherOrderCodes != null
                && motherOrderCodes.contains(dimension.motherOrderCode());
    }
}
