package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingDelivery;
import com.zhubao.erp.sampling.domain.SamplingDeliveryRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-delivery.repository", havingValue = "memory", matchIfMissing = true)
public class InMemorySamplingDeliveryRepository implements SamplingDeliveryRepository {

    private final ConcurrentHashMap<String, SamplingDelivery> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(SamplingDelivery delivery, WorkSession session) {
        SamplingDelivery previous = rows.putIfAbsent(delivery.deliveryId(), delivery);
        if (previous != null) {
            throw new BusinessException("交付编号冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(delivery.deliveryId(), delivery));
        }
    }

    @Override
    public Optional<SamplingDelivery> find(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(deliveryId.trim()));
    }

    @Override
    public List<SamplingDelivery> list(String samplingCode) {
        return rows.values().stream()
                .filter(delivery -> delivery.samplingCode().equals(samplingCode))
                .sorted(Comparator.comparing(SamplingDelivery::deliveryId))
                .toList();
    }

    @Override
    public List<SamplingDelivery> lockVersion(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session) {
        return list(samplingCode).stream()
                .filter(delivery -> delivery.versionKind() == kind && delivery.versionNo() == versionNo)
                .toList();
    }
}
