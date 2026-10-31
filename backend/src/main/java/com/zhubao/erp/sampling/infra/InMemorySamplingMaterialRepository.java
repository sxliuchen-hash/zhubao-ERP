package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingMaterialRepository;
import com.zhubao.erp.sampling.domain.SamplingMaterialUse;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-material.repository", havingValue = "memory", matchIfMissing = true)
public class InMemorySamplingMaterialRepository implements SamplingMaterialRepository {

    private final ConcurrentHashMap<String, SamplingMaterialUse> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(SamplingMaterialUse use, WorkSession session) {
        SamplingMaterialUse previous = rows.putIfAbsent(use.useId(), use);
        if (previous != null) {
            throw new BusinessException("领用编号冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(use.useId(), use));
        }
    }

    @Override
    public Optional<SamplingMaterialUse> find(String useId) {
        if (useId == null || useId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(useId.trim()));
    }

    @Override
    public Optional<SamplingMaterialUse> lock(String useId, WorkSession session) {
        return find(useId);
    }

    @Override
    public List<SamplingMaterialUse> list(String samplingCode) {
        return rows.values().stream()
                .filter(use -> use.samplingCode().equals(samplingCode))
                .sorted(Comparator.comparing(SamplingMaterialUse::useId))
                .toList();
    }

    @Override
    public List<SamplingMaterialUse> lockSource(String sourceId, Integer sourceLineNo, WorkSession session) {
        return rows.values().stream()
                .filter(use -> use.sourceId().equals(sourceId) && Objects.equals(use.sourceLineNo(), sourceLineNo))
                .toList();
    }

    @Override
    public SamplingMaterialUse save(SamplingMaterialUse next, long expectedVersion, WorkSession session) {
        SamplingMaterialUse current = rows.get(next.useId());
        if (current == null) {
            throw new BusinessException("领用不存在");
        }
        if (current.version() != expectedVersion) {
            throw new BusinessException("领用已变化，请刷新后重试");
        }
        rows.put(next.useId(), next);
        if (session != null) {
            session.onRollback(() -> rows.put(next.useId(), current));
        }
        return next;
    }
}
