package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;

public interface ConsumableCostRepository {

    void saveRule(String issueId, ConsumptionTiming timing, boolean costPosted, String receiptId, WorkSession session);

    ConsumptionTiming timingOf(String issueId);

    String receiptIdOf(String issueId);

    boolean costPosted(String issueId);

    void markCostPosted(String issueId, WorkSession session);

    void insert(ConsumableCostEvent event, WorkSession session);

    List<ConsumableCostEvent> events(String issueId);
}
