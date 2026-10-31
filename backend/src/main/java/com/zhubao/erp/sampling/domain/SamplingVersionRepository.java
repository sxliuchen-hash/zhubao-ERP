package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/** 打样版本与确认记录。版本正文不提供改写。 */
public interface SamplingVersionRepository {

    void insert(SamplingVersion version, WorkSession session);

    void insertDecision(SamplingVersionDecision decision, WorkSession session);

    Optional<SamplingVersion> find(String samplingCode, SamplingVersionKind kind, int versionNo);

    Optional<SamplingVersionDecision> findDecision(String samplingCode, SamplingVersionKind kind, int versionNo);

    List<SamplingVersion> list(String samplingCode);

    List<SamplingVersionDecision> listDecisions(String samplingCode);

    List<SamplingVersion> lockKind(String samplingCode, SamplingVersionKind kind, WorkSession session);

    Optional<SamplingVersion> lockOne(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session);

    Optional<SamplingVersionDecision> lockDecision(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session);
}
