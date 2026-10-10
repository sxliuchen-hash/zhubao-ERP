package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 对已经判过不合格的质检对照开返工。原单和质检单由服务按贴纸找回。
 */
public record QualityReworkCommand(
        @NotBlank(message = "请先扫贴纸") String code
) {
}
