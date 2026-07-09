package com.zhubao.erp.machine.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 将现场机器编号的未绑定设备产量记录绑定到机器档案 ID 的命令。
 *
 * <p>承接 M3.121 的机器档案匹配建议：复核确认候选机器档案 ID 后，通过本命令把同编号未绑定记录
 * 批量绑定到该机器档案。绑定为人工确认动作，受 {@code MACHINE_OUTPUT_SUBMIT} 权限守卫。</p>
 */
public record BindMachineOutputCommand(
        @NotBlank String machineNoSnapshot,
        @NotNull Long machineId,
        String operator
) {
}
