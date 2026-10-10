package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 岗位提交本工序。操作人取登录账号，不从请求里另填。
 */
public record ProcessStationReportCommand(
        @NotBlank String code,
        @NotBlank String process
) {
}
