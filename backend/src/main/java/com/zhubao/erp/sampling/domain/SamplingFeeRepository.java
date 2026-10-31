package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface SamplingFeeRepository {

    void insert(SamplingFeeDecision decision, WorkSession session);

    Optional<SamplingFeeDecision> findByDelivery(String deliveryId);

    Optional<SamplingFeeDecision> lockByDelivery(String deliveryId, WorkSession session);
}
