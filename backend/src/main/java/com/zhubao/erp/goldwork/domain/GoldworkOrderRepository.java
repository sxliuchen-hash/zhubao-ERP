package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoldworkOrderRepository {

    void insert(GoldworkOrder order, WorkSession session);

    Optional<GoldworkOrder> find(String goldworkCode);

    List<GoldworkOrder> list();

    List<String> lockDay(LocalDate day, WorkSession session);
}
