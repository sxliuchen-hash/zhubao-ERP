package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.List;

public interface QuantityDispositionRepository extends PendingQuantity {

    void save(QuantityDispositionLine line, WorkSession session);

    List<QuantityDispositionLine> listByBag(String bagId);

    @Override
    default BigDecimal pending(String bagId, String stepId) {
        return listByBag(bagId).stream()
                .filter(line -> line.stepId().equals(stepId))
                .map(QuantityDispositionLine::pending)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
