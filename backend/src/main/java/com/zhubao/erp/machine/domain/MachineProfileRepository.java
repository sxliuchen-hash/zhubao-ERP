package com.zhubao.erp.machine.domain;

import java.util.List;
import java.util.Optional;

public interface MachineProfileRepository {

    /** 新增机器档案，返回带自增主键的档案。 */
    MachineProfile create(MachineProfile profile);

    /** 更新机器档案（按 id）。 */
    MachineProfile update(MachineProfile profile);

    Optional<MachineProfile> findById(Long id);

    Optional<MachineProfile> findByMachineNo(String machineNo);

    List<MachineProfile> findAll();
}
