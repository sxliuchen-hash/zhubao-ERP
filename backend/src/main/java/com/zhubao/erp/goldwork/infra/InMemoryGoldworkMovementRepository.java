package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMovement;
import com.zhubao.erp.goldwork.domain.GoldworkMovementRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-movement.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGoldworkMovementRepository implements GoldworkMovementRepository {

    private final ConcurrentHashMap<String, GoldworkMovement> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(GoldworkMovement movement, WorkSession session) {
        GoldworkMovement previous = rows.putIfAbsent(movement.movementId(), movement);
        if (previous != null) {
            throw new BusinessException("投入产出冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(movement.movementId(), movement));
        }
    }

    @Override
    public Optional<GoldworkMovement> find(String movementId) {
        if (movementId == null || movementId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(movementId.trim()));
    }

    @Override
    public List<GoldworkMovement> list(String goldworkCode) {
        return rows.values().stream()
                .filter(row -> row.goldworkCode().equals(goldworkCode))
                .sorted(Comparator.comparing(GoldworkMovement::movementId))
                .toList();
    }
}
