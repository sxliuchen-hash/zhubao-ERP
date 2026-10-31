package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface SamplingDeliveryRepository {

    void insert(SamplingDelivery delivery, WorkSession session);

    Optional<SamplingDelivery> find(String deliveryId);

    List<SamplingDelivery> list(String samplingCode);

    List<SamplingDelivery> lockVersion(String samplingCode, SamplingVersionKind kind, int versionNo, WorkSession session);
}
