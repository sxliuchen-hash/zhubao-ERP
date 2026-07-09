package com.zhubao.erp.machine.domain;

/**
 * 设备产量机器档案绑定结果：记录被绑定的现场机器编号、目标机器档案 ID 和实际绑定的记录数量。
 */
public record MachineOutputBindingResult(
        String machineNoSnapshot,
        Long machineId,
        int boundRecords,
        String operator
) {
}
