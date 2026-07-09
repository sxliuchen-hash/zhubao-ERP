package com.zhubao.erp.machine.domain;

import java.util.List;
import java.util.Optional;

public interface MachineOutputRepository {

    MachineOutputRecord save(MachineOutputRecord record);

    Optional<MachineOutputRecord> findByCode(String code);

    List<MachineOutputRecord> findRecent(int limit);

    List<MachineOutputRecord> findByImportBatchNo(String importBatchNo);

    /**
     * 将指定现场机器编号的未绑定（machineId 为空）设备产量记录绑定到机器档案 ID。
     *
     * @param machineNoSnapshot 现场机器编号（如雕刻 1-8 号机）
     * @param machineId         目标机器档案 ID
     * @return 实际被绑定的记录数量
     */
    int bindMachineId(String machineNoSnapshot, Long machineId);
}
