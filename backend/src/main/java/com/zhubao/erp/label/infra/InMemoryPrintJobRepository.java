package com.zhubao.erp.label.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintRecycle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryPrintJobRepository implements PrintJobRepository {

    private final ConcurrentHashMap<String, PrintJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PrintRecycle> recycledJobs = new ConcurrentHashMap<>();

    @Override
    public Optional<PrintJob> findById(String id) {
        if (recycledJobs.containsKey(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public Optional<PrintJob> findByRequestId(String requestId) {
        return jobs.values().stream().filter(job -> requestId.equals(job.requestId())).findFirst();
    }

    @Override
    public synchronized PrintJob save(PrintJob job) {
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public synchronized PrintJob insertIfAbsent(PrintJob job) {
        PrintJob existing = findByRequestId(job.requestId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public synchronized Optional<PrintJob> claim(String stationId, String leaseUntil, String now) {
        Optional<PrintJob> pending = jobs.values().stream()
                .filter(job -> job.status() == PrintJobStatus.PENDING)
                .filter(job -> !recycledJobs.containsKey(job.id()))
                .findFirst();
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        PrintJob current = pending.get();
        PrintJob leased = new PrintJob(
                current.id(), current.requestId(), current.businessCode(), current.templateCode(), current.copies(),
                current.contentSnapshot(), PrintJobStatus.LEASED, stationId, leaseUntil, "", current.predecessorId(),
                current.retryReason(), current.createdBy(), current.createdAt(), now
        );
        jobs.put(leased.id(), leased);
        return Optional.of(leased);
    }

    @Override
    public List<PrintJob> list() {
        return jobs.values().stream().filter(job -> !recycledJobs.containsKey(job.id())).toList();
    }

    @Override
    public Optional<PrintJob> findAny(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<PrintJob> listByBusinessAny(String businessCode) {
        return jobs.values().stream().filter(job -> businessCode.equals(job.businessCode())).toList();
    }

    @Override
    public synchronized void recycle(String id, String operator, String at, String batch, String reason) {
        PrintJob job = jobs.get(id);
        if (job == null) {
            throw new BusinessException("打印任务不存在");
        }
        if (recycledJobs.containsKey(id)) {
            throw new BusinessException("打印任务已在回收站");
        }
        recycledJobs.put(id, new PrintRecycle(id, job.businessCode(), at, operator, batch, reason));
    }

    @Override
    public synchronized int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason) {
        int count = 0;
        for (PrintJob job : List.copyOf(jobs.values())) {
            if (businessCode.equals(job.businessCode()) && !recycledJobs.containsKey(job.id())) {
                recycledJobs.put(job.id(), new PrintRecycle(job.id(), job.businessCode(), at, operator, batch, reason));
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized void restore(String id) {
        if (recycledJobs.remove(id) == null) {
            throw new BusinessException("打印任务不在回收站");
        }
    }

    @Override
    public synchronized void restoreBatch(String batch) {
        recycledJobs.entrySet().removeIf(entry -> batch.equals(entry.getValue().batch()));
    }

    @Override
    public Optional<PrintRecycle> recycleMark(String id) {
        return Optional.ofNullable(recycledJobs.get(id));
    }

    @Override
    public List<PrintRecycle> recycled() {
        return List.copyOf(recycledJobs.values());
    }

    @Override
    public synchronized PrintJob replaceCopies(String id, int copies, String stationId, String updatedAt) {
        PrintJob current = jobs.get(id);
        if (current == null) {
            throw new BusinessException("打印任务不存在");
        }
        PrintJob next = new PrintJob(current.id(), current.requestId(), current.businessCode(), current.templateCode(),
                copies, current.contentSnapshot(), current.status(), stationId, current.leaseUntil(), current.failureReason(),
                current.predecessorId(), current.retryReason(), current.createdBy(), current.createdAt(), updatedAt);
        jobs.put(id, next);
        return next;
    }
}
