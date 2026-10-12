package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 质检面板上的返工报工或放行。原单由贴纸解析，数量重量取当前适用库存。
 * requestId 可空。有值时库存、业务事实和成功结果同一次提交。
 */
public record QualityFollowCommand(
        @NotBlank(message = "请先扫贴纸") String code,
        String requestId
) {
}
