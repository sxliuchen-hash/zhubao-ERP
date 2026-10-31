package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingVersion;
import com.zhubao.erp.sampling.domain.SamplingVersionDecision;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.domain.SamplingVersionRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-version.repository", havingValue = "memory", matchIfMissing = true)
public class InMemorySamplingVersionRepository implements SamplingVersionRepository {

    private final ConcurrentHashMap<String, SamplingVersion> versions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SamplingVersionDecision> decisions = new ConcurrentHashMap<>();

    @Override
    public void insert(SamplingVersion version, WorkSession session) {
        String key = key(version.samplingCode(), version.kind(), version.versionNo());
        SamplingVersion previous = versions.putIfAbsent(key, version);
        if (previous != null) {
            throw new BusinessException("版本号冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> versions.remove(key, version));
        }
    }

    @Override
    public void insertDecision(SamplingVersionDecision decision, WorkSession session) {
        String key = key(decision.samplingCode(), decision.kind(), decision.versionNo());
        SamplingVersionDecision previous = decisions.putIfAbsent(key, decision);
        if (previous != null) {
            throw new BusinessException("该版本已经有确认记录");
        }
        if (session != null) {
            session.onRollback(() -> decisions.remove(key, decision));
        }
    }

    @Override
    public Optional<SamplingVersion> find(String samplingCode, SamplingVersionKind kind, int versionNo) {
        return Optional.ofNullable(versions.get(key(samplingCode, kind, versionNo)));
    }

    @Override
    public Optional<SamplingVersionDecision> findDecision(String samplingCode, SamplingVersionKind kind, int versionNo) {
        return Optional.ofNullable(decisions.get(key(samplingCode, kind, versionNo)));
    }

    @Override
    public List<SamplingVersion> list(String samplingCode) {
        return versions.values().stream()
                .filter(version -> version.samplingCode().equals(samplingCode))
                .sorted(Comparator.comparing(SamplingVersion::kind).thenComparingInt(SamplingVersion::versionNo))
                .toList();
    }

    @Override
    public List<SamplingVersionDecision> listDecisions(String samplingCode) {
        return decisions.values().stream()
                .filter(decision -> decision.samplingCode().equals(samplingCode))
                .toList();
    }

    @Override
    public List<SamplingVersion> lockKind(String samplingCode, SamplingVersionKind kind, WorkSession session) {
        return versions.values().stream()
                .filter(version -> version.samplingCode().equals(samplingCode) && version.kind() == kind)
                .toList();
    }

    @Override
    public Optional<SamplingVersion> lockOne(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session) {
        return find(samplingCode, kind, versionNo);
    }

    @Override
    public Optional<SamplingVersionDecision> lockDecision(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session) {
        return findDecision(samplingCode, kind, versionNo);
    }

    private static String key(String samplingCode, SamplingVersionKind kind, int versionNo) {
        return samplingCode + "|" + kind.name() + "|" + versionNo;
    }
}
