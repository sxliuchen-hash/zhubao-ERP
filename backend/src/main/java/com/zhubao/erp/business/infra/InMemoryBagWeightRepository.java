package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BagWeightFact;
import com.zhubao.erp.business.domain.BagWeightRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.bag-weight.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryBagWeightRepository implements BagWeightRepository {

    private final ConcurrentHashMap<String, BagWeightFact> rows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<BagWeightFact>> records = new ConcurrentHashMap<>();

    @Override
    public Optional<BagWeightFact> find(String bagId) {
        return Optional.ofNullable(rows.get(bagId));
    }

    @Override
    public void save(BagWeightFact fact, WorkSession session) {
        BagWeightFact previous = rows.get(fact.bagId());
        rows.put(fact.bagId(), fact);
        CopyOnWriteArrayList<BagWeightFact> history = records.computeIfAbsent(fact.bagId(), ignored -> new CopyOnWriteArrayList<>());
        history.add(fact);
        if (session != null) {
            session.onRollback(() -> {
                history.remove(fact);
                if (previous == null) {
                    rows.remove(fact.bagId(), fact);
                } else {
                    rows.put(fact.bagId(), previous);
                }
            });
        }
    }

    @Override
    public List<BagWeightFact> history(String bagId) {
        return List.copyOf(records.getOrDefault(bagId, new CopyOnWriteArrayList<>()));
    }
}
