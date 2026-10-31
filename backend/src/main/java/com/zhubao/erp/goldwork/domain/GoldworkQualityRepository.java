package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface GoldworkQualityRepository {

    void insert(GoldworkQualityDecision decision, WorkSession session);

    Optional<GoldworkQualityDecision> find(String qualityId);

    List<GoldworkQualityDecision> list(String goldworkCode);
}
