package com.zhubao.erp.label.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 贴纸丢失后的补打命令。验证码沿用原单，本次原因写入补打日志。
 */
public record ReprintLabelCommand(
        @NotBlank(message = "缺少标签类型") String type,
        @NotBlank(message = "缺少单号") String primaryCode,
        @NotBlank(message = "补打必须填写原因") String reason,
        String operator
) {
}
