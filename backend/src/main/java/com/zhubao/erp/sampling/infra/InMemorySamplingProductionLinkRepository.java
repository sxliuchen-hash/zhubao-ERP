package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingProductionLink;
import com.zhubao.erp.sampling.domain.SamplingProductionLinkRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-production-link.repository", havingValue = "memory", matchIfMissing = true)
public class InMemorySamplingProductionLinkRepository implements SamplingProductionLinkRepository {

    private final ConcurrentHashMap<String, SamplingProductionLink> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(SamplingProductionLink link, WorkSession session) {
        SamplingProductionLink previous = rows.putIfAbsent(link.motherCode(), link);
        if (previous != null) {
            throw new BusinessException("这张母单已经引用样品版本");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(link.motherCode(), link));
        }
    }

    @Override
    public void delete(String motherCode, WorkSession session) {
        SamplingProductionLink removed = rows.remove(motherCode);
        if (removed == null) {
            throw new BusinessException("这张母单没有样品引用");
        }
        if (session != null) {
            session.onRollback(() -> rows.putIfAbsent(motherCode, removed));
        }
    }

    @Override
    public Optional<SamplingProductionLink> find(String motherCode) {
        if (motherCode == null || motherCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(motherCode.trim()));
    }

    @Override
    public Optional<SamplingProductionLink> lock(String motherCode, WorkSession session) {
        return find(motherCode);
    }
}
