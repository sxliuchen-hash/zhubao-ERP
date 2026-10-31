package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface GoldworkDeliveryRepository {

    void insert(GoldworkDelivery delivery, WorkSession session);

    Optional<GoldworkDelivery> find(String deliveryId);

    boolean existsForQuality(String qualityId, WorkSession session);

    List<GoldworkDelivery> list(String goldworkCode);
}
