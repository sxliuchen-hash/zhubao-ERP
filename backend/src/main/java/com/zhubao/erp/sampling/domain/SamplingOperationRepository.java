package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/** 打样报工。同一张照片只能用一次。 */
public interface SamplingOperationRepository {

    void insert(SamplingOperationReport report, WorkSession session);

    Optional<SamplingOperationReport> find(String reportId);

    boolean photoUsed(String attachmentCode, WorkSession session);

    List<SamplingOperationReport> list(String samplingCode);
}
