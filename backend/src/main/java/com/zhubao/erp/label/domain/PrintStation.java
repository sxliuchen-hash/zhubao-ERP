package com.zhubao.erp.label.domain;

public record PrintStation(String stationId, String name, boolean enabled, String remark, String tokenHash) {
    public PrintStation publicView() { return new PrintStation(stationId, name, enabled, remark, ""); }
}
