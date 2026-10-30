package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OutsourcingParty;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-source.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOutsourcingSourceRepository implements OutsourcingSourceRepository {

    private final ConcurrentHashMap<String, OutsourcingParty> parties = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<OutsourcingTask> tasks = new CopyOnWriteArrayList<>();

    @Override
    public void saveParty(OutsourcingParty party, int expectedVersion, WorkSession session) {
        OutsourcingParty previous = parties.get(party.partyId());
        int current = previous == null ? 0 : previous.version();
        if (current != expectedVersion) {
            throw conflict(expectedVersion, current);
        }
        parties.put(party.partyId(), party);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    parties.remove(party.partyId(), party);
                } else {
                    parties.put(party.partyId(), previous);
                }
            });
        }
    }

    @Override
    public Optional<OutsourcingParty> findParty(String partyId) {
        if (partyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parties.get(partyId));
    }

    @Override
    public Optional<OutsourcingParty> lockParty(String partyId, WorkSession session) {
        return findParty(partyId);
    }

    @Override
    public void saveTask(OutsourcingTask task, WorkSession session) {
        if (tasks.stream().anyMatch(row -> row.taskId().equals(task.taskId()) || row.requestId().equals(task.requestId()))) {
            throw new BusinessException("外协任务已存在");
        }
        tasks.add(task);
        if (session != null) {
            session.onRollback(() -> tasks.remove(task));
        }
    }

    @Override
    public Optional<OutsourcingTask> findTask(String taskId) {
        return tasks.stream().filter(row -> row.taskId().equals(taskId)).findFirst();
    }

    @Override
    public Optional<OutsourcingTask> findTaskByRequest(String requestId) {
        return tasks.stream().filter(row -> row.requestId().equals(requestId)).findFirst();
    }

    @Override
    public List<OutsourcingTask> listByTarget(String targetCode) {
        return tasks.stream()
                .filter(row -> row.targetCode().equals(targetCode))
                .sorted(Comparator.comparing(OutsourcingTask::taskId))
                .toList();
    }

    private static BusinessException conflict(int expectedVersion, int current) {
        if (expectedVersion == 0 && current > 0) {
            return new BusinessException("外协方已存在");
        }
        return new BusinessException("外协方已被其他人修改，请刷新后重试");
    }
}
