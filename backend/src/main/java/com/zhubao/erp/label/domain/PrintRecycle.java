package com.zhubao.erp.label.domain;

/** 打印任务的回收站标记。 */
public record PrintRecycle(
        String id,
        String businessCode,
        String recycledAt,
        String recycledBy,
        String batch,
        String reason
) {
}
