package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface BagWeightRepository {

    Optional<BagWeightFact> find(String bagId);

    void save(BagWeightFact fact, WorkSession session);

    /** 按称重先后返回全部记录。原记录不因后来的称重被覆盖。 */
    default List<BagWeightFact> history(String bagId) {
        return find(bagId).map(List::of).orElse(List.of());
    }
}
