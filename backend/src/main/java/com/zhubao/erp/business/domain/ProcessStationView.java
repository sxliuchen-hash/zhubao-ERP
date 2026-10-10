package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 岗位扫码后看到的单据。stations 只含当前账号绑定、且这张单要做的工序。
 */
public record ProcessStationView(
        String orderCode,
        String customerName,
        String dueDate,
        String processingRequirement,
        List<ProcessStationCard> stations,
        String message
) {
    public ProcessStationView {
        stations = stations == null ? List.of() : List.copyOf(stations);
    }
}
