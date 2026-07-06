package com.zhubao.erp.hardware.domain;

public record HardwarePocRecord(
        String code,
        String deviceCode,
        String scene,
        String testWindow,
        String result,
        String evidence,
        String blocker,
        String owner,
        String nextAction
) {
}
