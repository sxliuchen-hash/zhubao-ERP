package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.*;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryLabelStorageEditionRepository implements LabelStorageEditionRepository {
    private final ConcurrentHashMap<String, java.util.List<LabelStorageEdition>> history = new ConcurrentHashMap<>();
    public Optional<LabelStorageEdition> latest(String code, WorkSession session) { return history.getOrDefault(code, java.util.List.of()).stream().max(java.util.Comparator.comparingInt(LabelStorageEdition::edition)); }
    public java.util.List<LabelStorageEdition> history(String code) { return history.getOrDefault(code, java.util.List.of()); }
    public void save(LabelStorageEdition edition, WorkSession session) {
        var previous = history.get(edition.businessCode());
        var next = new java.util.ArrayList<>(previous == null ? java.util.List.of() : previous);
        next.add(edition); history.put(edition.businessCode(), java.util.List.copyOf(next));
        session.onRollback(() -> { if (previous == null) history.remove(edition.businessCode()); else history.put(edition.businessCode(), previous); });
    }
}
