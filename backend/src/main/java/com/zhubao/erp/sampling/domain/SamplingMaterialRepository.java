package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/** 打样领用引用。不保存实物余额。 */
public interface SamplingMaterialRepository {

    void insert(SamplingMaterialUse use, WorkSession session);

    Optional<SamplingMaterialUse> find(String useId);

    Optional<SamplingMaterialUse> lock(String useId, WorkSession session);

    List<SamplingMaterialUse> list(String samplingCode);

    List<SamplingMaterialUse> lockSource(String sourceId, Integer sourceLineNo, WorkSession session);

    SamplingMaterialUse save(SamplingMaterialUse next, long expectedVersion, WorkSession session);
}
