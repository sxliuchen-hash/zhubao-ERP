package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface GoldworkMaterialRepository {

    void insert(GoldworkMaterialLine line, WorkSession session);

    Optional<GoldworkMaterialLine> find(String lineId);

    List<GoldworkMaterialLine> list(String goldworkCode);
}
