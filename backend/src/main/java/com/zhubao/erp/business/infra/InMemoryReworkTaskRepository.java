package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ReworkTask;
import com.zhubao.erp.business.domain.ReworkTaskRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.rework-task.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryReworkTaskRepository implements ReworkTaskRepository {

    private final CopyOnWriteArrayList<ReworkTask> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(ReworkTask task, WorkSession session) {
        if (rows.stream().anyMatch(row -> row.requestId().equals(task.requestId()))) {
            throw new BusinessException("返工任务已存在");
        }
        rows.add(task);
        if (session != null) {
            session.onRollback(() -> rows.remove(task));
        }
    }

    @Override
    public Optional<ReworkTask> findByRequest(String requestId) {
        return rows.stream().filter(row -> row.requestId().equals(requestId)).findFirst();
    }

    @Override
    public List<ReworkTask> listBySource(String sourceBagId) {
        return rows.stream().filter(row -> row.sourceBagId().equals(sourceBagId)).toList();
    }

    @Override
    public List<ReworkTask> listByReworkBag(String reworkBagId) {
        return rows.stream().filter(row -> row.reworkBagId().equals(reworkBagId)).toList();
    }
}
