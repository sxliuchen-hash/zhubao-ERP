package com.zhubao.erp.machine.domain;

/**
 * 机器档案主数据：为设备产量绑定（M3.134）提供真实可选的机器档案来源。
 *
 * <p>{@code machineNo} 是现场机器编号（如雕刻 1-8 号机），与设备产量记录的 {@code machineNoSnapshot}
 * 对应；{@code id} 是机器档案主键，即绑定命令中的 {@code machineId}。</p>
 */
public record MachineProfile(
        Long id,
        String machineNo,
        String machineName,
        String machineType,
        String location,
        boolean enabled,
        String remark,
        String createdBy,
        String createdAt
) {
}
