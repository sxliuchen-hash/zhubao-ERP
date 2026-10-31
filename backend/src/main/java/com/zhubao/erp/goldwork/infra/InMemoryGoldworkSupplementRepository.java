package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkSupplement;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-supplement.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGoldworkSupplementRepository implements GoldworkSupplementRepository {

    private final ConcurrentHashMap<String, GoldworkSupplement> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(GoldworkSupplement supplement, WorkSession session) {
        GoldworkSupplement previous = rows.putIfAbsent(supplement.supplementId(), supplement);
        if (previous != null) {
            throw new BusinessException("补料冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(supplement.supplementId(), supplement));
        }
    }

    @Override
    public Optional<GoldworkSupplement> find(String supplementId) {
        if (supplementId == null || supplementId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(supplementId.trim()));
    }

    @Override
    public List<GoldworkSupplement> list(String goldworkCode) {
        return rows.values().stream()
                .filter(row -> row.goldworkCode().equals(goldworkCode))
                .sorted(Comparator.comparing(GoldworkSupplement::supplementId))
                .toList();
    }
}
