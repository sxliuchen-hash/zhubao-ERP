package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryProcessingIntakePendingRepository implements ProcessingIntakePendingRepository {
    private final ConcurrentHashMap<String, ProcessingIntakePending> rows = new ConcurrentHashMap<>();
    public Optional<ProcessingIntakePending> find(String code, WorkSession session) { return Optional.ofNullable(rows.get(code)); }
    public List<ProcessingIntakePending> list(String account) { return rows.values().stream().filter(row -> account.equals(row.account()) && "PENDING".equals(row.status())).sorted(java.util.Comparator.comparing(ProcessingIntakePending::updatedAt).reversed()).toList(); }
    public void save(ProcessingIntakePending pending, WorkSession session) {
        var previous = rows.put(pending.code(), pending);
        session.onRollback(() -> { if (previous == null) rows.remove(pending.code()); else rows.put(pending.code(), previous); });
    }
}
