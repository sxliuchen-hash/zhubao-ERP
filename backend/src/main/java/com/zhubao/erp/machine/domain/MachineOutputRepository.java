package com.zhubao.erp.machine.domain;

import java.util.List;
import java.util.Optional;

public interface MachineOutputRepository {

    MachineOutputRecord save(MachineOutputRecord record);

    Optional<MachineOutputRecord> findByCode(String code);

    List<MachineOutputRecord> findRecent(int limit);

    List<MachineOutputRecord> findByImportBatchNo(String importBatchNo);
}
