package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 提交复检。不覆盖初检。requestId 可空；有值时与本轮事实同一次提交。
 */
public record QualityRecheckCommand(
        @NotBlank(message = "请先扫贴纸") String code,
        @NotNull(message = "每一条加工要求都要有结果才能提交") List<QualityCheckLineCommand> lines,
        String requestId
) {
}
