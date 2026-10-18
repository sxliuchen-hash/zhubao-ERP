package com.zhubao.erp.business.domain;

/** 回收站标记。单据仍在库里，日常查询不再返回。 */
public record OrderRecycleMark(
        String targetType,
        String code,
        String recycledAt,
        String recycledBy,
        String batch,
        String reason
) {
}
