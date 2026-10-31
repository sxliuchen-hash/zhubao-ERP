package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface GoldworkRecoveryRepository {

    void insert(GoldworkRecovery recovery, WorkSession session);

    Optional<GoldworkRecovery> find(String recoveryId);

    List<GoldworkRecovery> list(String goldworkCode);
}
