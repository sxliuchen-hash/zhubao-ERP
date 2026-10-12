package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 岗位提交本工序。操作人取登录账号，不从请求里另填。
 * requestId 可空。有值时同一次确认回放；不传则仍走各自提交，不回放。
 */
public record ProcessStationReportCommand(
        @NotBlank String code,
        @NotBlank String process,
        String requestId
) {
    public ProcessStationReportCommand(String code, String process) {
        this(code, process, null);
    }
}
