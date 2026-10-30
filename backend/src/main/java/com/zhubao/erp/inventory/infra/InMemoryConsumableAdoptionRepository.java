package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.inventory.domain.ConsumableAdoption;
import com.zhubao.erp.inventory.domain.ConsumableAdoptionRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.consumable-adoption.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryConsumableAdoptionRepository implements ConsumableAdoptionRepository {

    private final ConcurrentHashMap<String, ConsumableAdoption> rows = new ConcurrentHashMap<>();

    @Override
    public void save(ConsumableAdoption adoption, WorkSession session) {
        ConsumableAdoption previous = rows.put(adoption.oldItemCode(), adoption);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    rows.remove(adoption.oldItemCode(), adoption);
                } else {
                    rows.put(adoption.oldItemCode(), previous);
                }
            });
        }
    }

    @Override
    public Optional<ConsumableAdoption> findByOldCode(String oldItemCode) {
        return Optional.ofNullable(rows.get(oldItemCode));
    }
}
