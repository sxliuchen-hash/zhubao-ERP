package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkRecovery;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-recovery.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGoldworkRecoveryRepository implements GoldworkRecoveryRepository {

    private final ConcurrentHashMap<String, GoldworkRecovery> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(GoldworkRecovery recovery, WorkSession session) {
        GoldworkRecovery previous = rows.putIfAbsent(recovery.recoveryId(), recovery);
        if (previous != null) {
            throw new BusinessException("回收记录冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(recovery.recoveryId(), recovery));
        }
    }

    @Override
    public Optional<GoldworkRecovery> find(String recoveryId) {
        if (recoveryId == null || recoveryId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(recoveryId.trim()));
    }

    @Override
    public List<GoldworkRecovery> list(String goldworkCode) {
        return rows.values().stream()
                .filter(row -> row.goldworkCode().equals(goldworkCode))
                .sorted(Comparator.comparing(GoldworkRecovery::recoveryId))
                .toList();
    }
}
