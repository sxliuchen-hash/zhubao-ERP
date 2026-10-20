package com.zhubao.erp.business.app;

import java.util.List;

/** 管理员按日期、客户和建单人浏览未回收母单。超过上限时只返回最近一批。 */
public record MaintainedMotherBrowse(
        List<MaintainedMotherSummary> items,
        int total,
        boolean truncated
) {
}
