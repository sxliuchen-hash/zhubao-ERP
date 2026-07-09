package com.zhubao.erp.machine.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增或更新机器档案的命令。{@code id} 为空表示新增，非空表示更新。
 * 受 {@code MACHINE_OUTPUT_SUBMIT} 权限守卫。
 */
public record SaveMachineProfileCommand(
        Long id,
        @NotBlank String machineNo,
        @NotBlank String machineName,
        String machineType,
        String location,
        Boolean enabled,
        String remark,
        String operator
) {
}
