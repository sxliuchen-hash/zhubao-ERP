package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 提交质检对照。inspector 即使另传了名字也不采用，质检员取当前登录账号的操作人。
 */
public record QualityCheckCommand(
        @NotBlank(message = "请先扫贴纸") String code,
        String inspector,
        @NotNull(message = "每一条加工要求都要有结果才能提交") List<QualityCheckLineCommand> lines
) {
}
