package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.business.domain.OrderChangeStatus;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOrderChangeRepository implements OrderChangeRepository {

    private final ConcurrentHashMap<String, OrderChange> changes = new ConcurrentHashMap<>();

    @Override
    public OrderChange save(OrderChange change) {
        changes.put(change.id(), change);
        return change;
    }

    @Override
    public OrderChange save(OrderChange change, WorkSession session) {
        OrderChange previous = changes.get(change.id());
        OrderChange saved = save(change);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    changes.remove(change.id());
                } else {
                    changes.put(change.id(), previous);
                }
            });
        }
        return saved;
    }

    @Override
    public Optional<OrderChange> findById(String id) {
        return Optional.ofNullable(changes.get(id));
    }

    @Override
    public Optional<OrderChange> findByRequestId(String requestId) {
        return changes.values().stream().filter(change -> requestId.equals(change.requestId())).findFirst();
    }

    @Override
    public List<OrderChange> listByMother(String motherOrderCode) {
        return changes.values().stream().filter(change -> motherOrderCode.equals(change.motherOrderCode())).toList();
    }

    @Override
    public int latestAppliedVersion(String businessCode) {
        return changes.values().stream()
                .filter(change -> change.status() == OrderChangeStatus.APPLIED)
                .filter(change -> businessCode.equals(change.motherOrderCode()) || businessCode.equals(change.workOrderCode()))
                .mapToInt(OrderChange::versionNo)
                .max()
                .orElse(0);
    }
}
