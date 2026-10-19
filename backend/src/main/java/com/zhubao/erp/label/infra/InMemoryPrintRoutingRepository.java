package com.zhubao.erp.label.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintRecycle;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintRoutingRepository;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTargetJob;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryPrintRoutingRepository implements PrintRoutingRepository {
    private final Map<String, PrintStation> stations = new ConcurrentHashMap<>();
    private final Map<String, PrintQueue> queues = new ConcurrentHashMap<>();
    private final Map<String, PrintBatch> batches = new ConcurrentHashMap<>();
    private final Map<String, String> requests = new ConcurrentHashMap<>();
    private final Map<String, PrintTargetJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, PrintRecycle> recycled = new ConcurrentHashMap<>();

    @Override public List<PrintStation> stations() { return stations.values().stream().sorted(Comparator.comparing(PrintStation::stationId)).toList(); }
    @Override public Optional<PrintStation> station(String id) { return Optional.ofNullable(stations.get(id)); }
    @Override public synchronized PrintStation saveStation(PrintStation station) { stations.put(station.stationId(), station); return station; }
    @Override public List<PrintQueue> queues() { return queues.values().stream().sorted(Comparator.comparing(PrintQueue::stationId).thenComparing(PrintQueue::queueId)).toList(); }
    @Override public synchronized PrintQueue saveQueue(PrintQueue queue) { queues.put(queue.stationId() + "|" + queue.queueId(), queue); return queue; }
    @Override public Optional<PrintBatch> batchByRequest(String requestId) { return Optional.ofNullable(requests.get(requestId)).flatMap(this::batch); }
    @Override public Optional<PrintBatch> batch(String id) { return Optional.ofNullable(batches.get(id)).map(batch -> batch.withJobs(jobs.values().stream().filter(job -> id.equals(job.batchId()) && !recycled.containsKey(job.id())).sorted(Comparator.comparing(PrintTargetJob::id)).toList())); }
    @Override public synchronized PrintBatch insertBatch(PrintBatch batch) {
        PrintBatch existing = batchByRequest(batch.requestId()).orElse(null);
        if (existing != null) return existing;
        batches.put(batch.id(), batch.withJobs(List.of()));
        batch.jobs().forEach(job -> jobs.put(job.id(), job));
        requests.put(batch.requestId(), batch.id());
        return batch(batch.id()).orElseThrow();
    }
    @Override public synchronized PrintBatch insertOriginalBatch(PrintBatch batch) {
        PrintBatch existing = batchByRequest(batch.requestId()).orElse(null);
        if (existing != null) return existing;
        for (PrintTargetJob job : jobsForBusinessAny(batch.businessCode())) {
            if (recycled.containsKey(job.id())) continue;
            if ((job.status() == PrintJobStatus.FAILED || job.status() == PrintJobStatus.UNCERTAIN)
                    && (job.paperCheckResult() == null || job.paperCheckResult().isBlank())) {
                throw new BusinessException("该单有失败或不确定的打印任务，请先核纸，不能直接再建批次");
            }
        }
        return insertBatch(batch);
    }
    @Override public synchronized Optional<PrintBatch> rerouteJob(String originalId, PrintJobStatus expected,
                                                                   PrintBatch replacement, String reason, String now) {
        PrintTargetJob original = jobs.get(originalId);
        if (original == null || original.status() != expected || (expected != PrintJobStatus.PENDING
                && !"确认未出纸".equals(original.paperCheckResult())))
            return Optional.empty();
        if (batchByRequest(replacement.requestId()).isPresent()) return Optional.empty();
        jobs.put(originalId, new PrintTargetJob(original.id(), original.batchId(), original.targetStationId(),
                original.targetQueueId(), original.copies(), PrintJobStatus.CANCELED, original.claimedStationId(),
                original.leaseUntil(), original.failureReason(), original.paperCheckResult(), original.predecessorId(),
                reason, original.createdAt(), now));
        return Optional.of(insertBatch(replacement));
    }
    @Override public Optional<PrintTargetJob> job(String id) {
        PrintTargetJob job = jobs.get(id);
        return job == null || recycled.containsKey(id) ? Optional.empty() : Optional.of(job);
    }
    @Override public synchronized Optional<PrintTargetJob> claim(String stationId, String leaseUntil, String now) {
        PrintTargetJob pending = jobs.values().stream().filter(job -> job.status() == PrintJobStatus.PENDING
                && stationId.equals(job.targetStationId()) && !recycled.containsKey(job.id()))
                .min(Comparator.comparing(PrintTargetJob::createdAt).thenComparing(PrintTargetJob::id)).orElse(null);
        if (pending == null) return Optional.empty();
        return Optional.of(saveJob(pending.withStatus(PrintJobStatus.LEASED, stationId, leaseUntil, "", "", now)));
    }
    @Override public synchronized int expireLeases(String now) {
        int count = 0;
        for (PrintTargetJob job : List.copyOf(jobs.values())) {
            if (job.status() == PrintJobStatus.LEASED && !job.leaseUntil().isBlank()
                    && job.leaseUntil().compareTo(now) < 0) {
                jobs.put(job.id(), job.withStatus(PrintJobStatus.UNCERTAIN, job.claimedStationId(),
                        job.leaseUntil(), "租约到期，出纸状态不确定", job.paperCheckResult(), now));
                count++;
            }
        }
        return count;
    }
    @Override public synchronized PrintTargetJob saveJob(PrintTargetJob job) { jobs.put(job.id(), job); return job; }
    @Override public synchronized Optional<PrintTargetJob> saveJobIfStatus(PrintTargetJob job, PrintJobStatus expected) {
        PrintTargetJob current = jobs.get(job.id());
        if (current == null || current.status() != expected) return Optional.empty();
        jobs.put(job.id(), job);
        return Optional.of(job);
    }
    @Override public synchronized Optional<PrintTargetJob> savePaperCheckIfBlank(PrintTargetJob job, PrintJobStatus expected) {
        PrintTargetJob current = jobs.get(job.id());
        if (current == null || current.status() != expected || !current.paperCheckResult().isBlank())
            return Optional.empty();
        jobs.put(job.id(), job);
        return Optional.of(job);
    }
    @Override public Optional<PrintTargetJob> jobAny(String id) { return Optional.ofNullable(jobs.get(id)); }
    @Override public synchronized Optional<PrintTargetJob> lockJobAny(String id, WorkSession session) {
        return jobAny(id);
    }
    @Override public synchronized void lockByBusinessCodes(List<String> businessCodes, WorkSession session) {
        if (session == null) throw new IllegalStateException("打印任务锁定必须处于业务工作单元");
    }
    @Override public List<PrintTargetJob> jobsForBusinessAny(String businessCode) {
        return batches.values().stream().filter(batch -> businessCode.equals(batch.businessCode()))
                .flatMap(batch -> jobs.values().stream().filter(job -> batch.id().equals(job.batchId()))).toList();
    }
    @Override public synchronized void recycleJob(String id, String operator, String at, String batch, String reason) {
        PrintTargetJob job = jobs.get(id);
        if (job == null) throw new BusinessException("打印任务不存在");
        if (recycled.containsKey(id)) throw new BusinessException("打印任务已在回收站");
        recycled.put(id, new PrintRecycle(id, businessCodeOf(job), at, operator, batch, reason));
    }
    @Override public synchronized void recycleJob(String id, String operator, String at, String batch,
                                                  String reason, WorkSession session) {
        recycleJob(id, operator, at, batch, reason);
        session.onRollback(() -> recycled.remove(id));
    }
    @Override public synchronized int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason) {
        int count = 0;
        for (PrintTargetJob job : List.copyOf(jobs.values())) {
            if (businessCode.equals(businessCodeOf(job)) && !recycled.containsKey(job.id())) {
                recycled.put(job.id(), new PrintRecycle(job.id(), businessCode, at, operator, batch, reason));
                count++;
            }
        }
        return count;
    }
    @Override public synchronized int recycleByBusinessCode(String businessCode, String operator, String at,
                                                            String batch, String reason, WorkSession session) {
        int count = recycleByBusinessCode(businessCode, operator, at, batch, reason);
        session.onRollback(() -> {
            synchronized (this) {
                recycled.entrySet().removeIf(entry -> batch.equals(entry.getValue().batch()));
            }
        });
        return count;
    }
    @Override public synchronized void restoreJob(String id) {
        if (recycled.remove(id) == null) throw new BusinessException("打印任务不在回收站");
    }
    @Override public synchronized void restoreJob(String id, WorkSession session) {
        PrintRecycle previous = recycled.get(id);
        restoreJob(id);
        session.onRollback(() -> recycled.put(id, previous));
    }
    @Override public synchronized void restoreBatch(String batch) {
        recycled.entrySet().removeIf(entry -> batch.equals(entry.getValue().batch()));
    }
    @Override public synchronized void restoreBatch(String batch, WorkSession session) {
        var previous = recycled.values().stream().filter(mark -> batch.equals(mark.batch())).toList();
        restoreBatch(batch);
        session.onRollback(() -> {
            synchronized (this) {
                previous.forEach(mark -> recycled.put(mark.id(), mark));
            }
        });
    }
    @Override public Optional<PrintRecycle> jobRecycle(String id) { return Optional.ofNullable(recycled.get(id)); }
    @Override public List<PrintRecycle> recycledJobs() { return List.copyOf(recycled.values()); }
    @Override public synchronized PrintTargetJob replaceTarget(String id, int copies, String stationId,
                                                                String queueId, String updatedAt, WorkSession session) {
        PrintTargetJob previous = jobs.get(id);
        if (previous == null || previous.status() != PrintJobStatus.PENDING || recycled.containsKey(id)) {
            throw new BusinessException("任务已领取、状态不明或已回收，不能直接改单");
        }
        PrintTargetJob next = new PrintTargetJob(previous.id(), previous.batchId(), stationId, queueId, copies,
                previous.status(), previous.claimedStationId(), previous.leaseUntil(), previous.failureReason(),
                previous.paperCheckResult(), previous.predecessorId(), previous.retryReason(), previous.createdAt(), updatedAt);
        jobs.put(id, next);
        session.onRollback(() -> jobs.put(id, previous));
        return next;
    }
    private String businessCodeOf(PrintTargetJob job) {
        return batches.values().stream().filter(batch -> job.batchId().equals(batch.id())).map(PrintBatch::businessCode).findFirst().orElse("");
    }
}
