package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface GoldworkSupplementRepository {

    void insert(GoldworkSupplement supplement, WorkSession session);

    Optional<GoldworkSupplement> find(String supplementId);

    List<GoldworkSupplement> list(String goldworkCode);
}
