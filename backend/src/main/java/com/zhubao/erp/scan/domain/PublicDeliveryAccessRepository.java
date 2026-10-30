package com.zhubao.erp.scan.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface PublicDeliveryAccessRepository {

    void save(PublicDeliveryAccess access, WorkSession session);

    Optional<PublicDeliveryAccess> find(String accessId);

    void disable(String accessId, int expectedVersion, WorkSession session);
}
