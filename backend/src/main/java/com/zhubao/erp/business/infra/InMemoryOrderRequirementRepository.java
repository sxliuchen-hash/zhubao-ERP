package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.OrderRequirementVersion;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOrderRequirementRepository implements OrderRequirementRepository {

    private final ConcurrentHashMap<String, OrderRequirementVersion> versions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExecutionRound> rounds = new ConcurrentHashMap<>();

    @Override
    public void saveVersion(OrderRequirementVersion version, WorkSession session) {
        OrderRequirementVersion previous = versions.get(version.id());
        versions.put(version.id(), version);
        if (session != null) {
            session.onRollback(() -> restore(versions, version.id(), previous));
        }
    }

    @Override
    public Optional<OrderRequirementVersion> current(String motherOrderCode, String workOrderCode) {
        String work = workOrderCode == null ? "" : workOrderCode;
        return versions.values().stream()
                .filter(version -> motherOrderCode.equals(version.motherOrderCode()) && work.equals(version.workOrderCode()))
                .max(Comparator.comparingInt(OrderRequirementVersion::versionNo));
    }

    @Override
    public void saveRound(ExecutionRound round, WorkSession session) {
        ExecutionRound previous = rounds.get(round.id());
        rounds.put(round.id(), round);
        if (session != null) {
            session.onRollback(() -> restore(rounds, round.id(), previous));
        }
    }

    @Override
    public List<ExecutionRound> listByMother(String motherOrderCode) {
        return rounds.values().stream()
                .filter(round -> motherOrderCode.equals(round.motherOrderCode()))
                .toList();
    }

    private static <T> void restore(ConcurrentHashMap<String, T> store, String id, T previous) {
        if (previous == null) {
            store.remove(id);
        } else {
            store.put(id, previous);
        }
    }
}