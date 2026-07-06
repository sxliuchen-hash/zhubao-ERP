package com.zhubao.erp.approval.infra;

import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.domain.ApprovalTaskRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryApprovalTaskRepository implements ApprovalTaskRepository {

    private final Map<String, ApprovalTask> tasks = new LinkedHashMap<>();

    @Override
    public synchronized ApprovalTask save(ApprovalTask task) {
        tasks.put(task.approvalCode(), task);
        return task;
    }

    @Override
    public synchronized Optional<ApprovalTask> findByCode(String approvalCode) {
        return Optional.ofNullable(tasks.get(approvalCode));
    }

    @Override
    public synchronized List<ApprovalTask> findAll() {
        return new ArrayList<>(tasks.values());
    }
}
