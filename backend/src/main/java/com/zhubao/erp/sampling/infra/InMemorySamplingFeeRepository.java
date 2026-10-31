package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingFeeDecision;
import com.zhubao.erp.sampling.domain.SamplingFeeRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-fee.repository", havingValue = "memory", matchIfMissing = true)
public class InMemorySamplingFeeRepository implements SamplingFeeRepository {

    private final ConcurrentHashMap<String, SamplingFeeDecision> byDelivery = new ConcurrentHashMap<>();

    @Override
    public void insert(SamplingFeeDecision decision, WorkSession session) {
        SamplingFeeDecision previous = byDelivery.putIfAbsent(decision.deliveryId(), decision);
        if (previous != null) {
            throw new BusinessException("这次交付的样费已经确认");
        }
        if (session != null) {
            session.onRollback(() -> byDelivery.remove(decision.deliveryId(), decision));
        }
    }

    @Override
    public Optional<SamplingFeeDecision> findByDelivery(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byDelivery.get(deliveryId.trim()));
    }

    @Override
    public Optional<SamplingFeeDecision> lockByDelivery(String deliveryId, WorkSession session) {
        return findByDelivery(deliveryId);
    }
}
