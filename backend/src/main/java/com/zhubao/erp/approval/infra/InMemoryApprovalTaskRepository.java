package com.zhubao.erp.approval.infra;

import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.domain.ApprovalTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.approval.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryApprovalTaskRepository implements ApprovalTaskRepository {

    private final Map<String, ApprovalTask> tasks = new LinkedHashMap<>();

    @Override
    public synchronized ApprovalTask insert(ApprovalTask task, com.zhubao.erp.work.domain.WorkSession session) {
        if (session == null) throw new IllegalStateException("审批申请必须处于业务工作单元");
        if (session.connection().isPresent()) throw new IllegalStateException("内存审批仓储不能接入 JDBC 工作单元，请统一仓储配置");
        if (tasks.putIfAbsent(task.approvalCode(), task) != null) {
            throw new com.zhubao.erp.common.error.BusinessException("申请单号已存在，请核对原申请");
        }
        session.onRollback(() -> { synchronized (this) { tasks.remove(task.approvalCode(), task); } });
        return task;
    }

    @Override
    public synchronized ApprovalTask save(ApprovalTask task) {
        tasks.put(task.approvalCode(), task);
        return task;
    }

    @Override
    public synchronized Optional<ApprovalTask> decideIfWaiting(ApprovalTask task) {
        ApprovalTask current = tasks.get(task.approvalCode());
        if (current == null || current.status() != ApprovalStatus.WAITING_APPROVAL) {
            return Optional.empty();
        }
        tasks.put(task.approvalCode(), task);
        return Optional.of(task);
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
