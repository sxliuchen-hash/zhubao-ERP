package com.zhubao.erp.label.domain;

public record PrintQueue(String stationId, String queueId, String name, String labelType,
                         int widthMm, int heightMm, boolean enabled, boolean defaultQueue) {
}
