package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkQualityDecision;
import com.zhubao.erp.goldwork.domain.GoldworkQualityRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-quality.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGoldworkQualityRepository implements GoldworkQualityRepository {

    private final ConcurrentHashMap<String, GoldworkQualityDecision> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(GoldworkQualityDecision decision, WorkSession session) {
        GoldworkQualityDecision previous = rows.putIfAbsent(decision.qualityId(), decision);
        if (previous != null) {
            throw new BusinessException("质量记录冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(decision.qualityId(), decision));
        }
    }

    @Override
    public Optional<GoldworkQualityDecision> find(String qualityId) {
        if (qualityId == null || qualityId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(qualityId.trim()));
    }

    @Override
    public List<GoldworkQualityDecision> list(String goldworkCode) {
        return rows.values().stream()
                .filter(row -> row.goldworkCode().equals(goldworkCode))
                .sorted(Comparator.comparing(GoldworkQualityDecision::qualityId))
                .toList();
    }
}
