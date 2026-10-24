package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.DeskTask;
import com.zhubao.erp.business.domain.DeskTaskRepository;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryDeskTaskRepository implements DeskTaskRepository {

    private final Map<String, DeskTask> byCode = new LinkedHashMap<>();
    private final Map<String, String> byRequest = new LinkedHashMap<>();
    private final Map<String, String> byCompleteRequest = new LinkedHashMap<>();
    private final Map<String, String> byCancelRequest = new LinkedHashMap<>();

    @Override
    public synchronized DeskTask insert(DeskTask task) {
        if (byCode.containsKey(task.taskCode())) {
            throw new BusinessException("待办单号已存在");
        }
        if (byRequest.containsKey(task.requestId())) {
            throw new BusinessException("请求编号已使用");
        }
        store(task);
        return task;
    }

    @Override
    public synchronized Optional<DeskTask> updateIfVersion(DeskTask task, int expectedVersion) {
        DeskTask current = byCode.get(task.taskCode());
        if (current == null || current.version() != expectedVersion) {
            return Optional.empty();
        }
        claim(byCompleteRequest, current.completeRequestId(), task.completeRequestId(), task.taskCode(), "完成请求编号已使用");
        claim(byCancelRequest, current.cancelRequestId(), task.cancelRequestId(), task.taskCode(), "作废请求编号已使用");
        forget(byCompleteRequest, current.completeRequestId(), current.taskCode());
        forget(byCancelRequest, current.cancelRequestId(), current.taskCode());
        store(task);
        return Optional.of(task);
    }

    @Override
    public synchronized Optional<DeskTask> findByCode(String taskCode) {
        return Optional.ofNullable(byCode.get(text(taskCode)));
    }

    @Override
    public synchronized Optional<DeskTask> findByRequestId(String requestId) {
        return lookup(byRequest, requestId);
    }

    @Override
    public synchronized Optional<DeskTask> findByCompleteRequestId(String requestId) {
        return lookup(byCompleteRequest, requestId);
    }

    @Override
    public synchronized Optional<DeskTask> findByCancelRequestId(String requestId) {
        return lookup(byCancelRequest, requestId);
    }

    @Override
    public synchronized List<DeskTask> listAll() {
        return List.copyOf(byCode.values());
    }

    private void store(DeskTask task) {
        byCode.put(task.taskCode(), task);
        byRequest.put(task.requestId(), task.taskCode());
        if (!task.completeRequestId().isEmpty()) {
            byCompleteRequest.put(task.completeRequestId(), task.taskCode());
        }
        if (!task.cancelRequestId().isEmpty()) {
            byCancelRequest.put(task.cancelRequestId(), task.taskCode());
        }
    }

    private void claim(Map<String, String> index, String previous, String next, String taskCode, String message) {
        if (next.isEmpty() || next.equals(previous)) {
            return;
        }
        String owner = index.get(next);
        if (owner != null && !owner.equals(taskCode)) {
            throw new BusinessException(message);
        }
    }

    private void forget(Map<String, String> index, String requestId, String taskCode) {
        if (!requestId.isEmpty() && taskCode.equals(index.get(requestId))) {
            index.remove(requestId);
        }
    }

    private Optional<DeskTask> lookup(Map<String, String> index, String requestId) {
        String code = index.get(text(requestId));
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(code));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
