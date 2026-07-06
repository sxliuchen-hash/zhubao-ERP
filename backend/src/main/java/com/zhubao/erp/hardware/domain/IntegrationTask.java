package com.zhubao.erp.hardware.domain;

public record IntegrationTask(
        String id,
        String title,
        String target,
        int health,
        String lastRun,
        String nextAction,
        String owner
) {
}
