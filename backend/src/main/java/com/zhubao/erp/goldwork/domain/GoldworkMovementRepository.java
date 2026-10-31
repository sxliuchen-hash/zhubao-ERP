package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface GoldworkMovementRepository {

    void insert(GoldworkMovement movement, WorkSession session);

    Optional<GoldworkMovement> find(String movementId);

    List<GoldworkMovement> list(String goldworkCode);
}
