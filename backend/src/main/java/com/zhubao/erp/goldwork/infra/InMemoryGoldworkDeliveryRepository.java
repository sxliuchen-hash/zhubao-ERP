package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkDelivery;
import com.zhubao.erp.goldwork.domain.GoldworkDeliveryRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-delivery.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGoldworkDeliveryRepository implements GoldworkDeliveryRepository {

    private final ConcurrentHashMap<String, GoldworkDelivery> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(GoldworkDelivery delivery, WorkSession session) {
        GoldworkDelivery previous = rows.putIfAbsent(delivery.deliveryId(), delivery);
        if (previous != null) {
            throw new BusinessException("交付冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(delivery.deliveryId(), delivery));
        }
    }

    @Override
    public Optional<GoldworkDelivery> find(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(deliveryId.trim()));
    }

    @Override
    public boolean existsForQuality(String qualityId, WorkSession session) {
        if (qualityId == null || qualityId.isBlank()) {
            return false;
        }
        return rows.values().stream().anyMatch(row -> qualityId.equals(row.qualityId()));
    }

    @Override
    public List<GoldworkDelivery> list(String goldworkCode) {
        return rows.values().stream()
                .filter(row -> row.goldworkCode().equals(goldworkCode))
                .sorted(Comparator.comparing(GoldworkDelivery::deliveryId))
                .toList();
    }
}
