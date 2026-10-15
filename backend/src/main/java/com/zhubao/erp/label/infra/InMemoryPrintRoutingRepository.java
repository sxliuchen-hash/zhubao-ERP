package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintRoutingRepository;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTargetJob;
import com.zhubao.erp.label.domain.PrintJobStatus;
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

    @Override public List<PrintStation> stations() { return stations.values().stream().sorted(Comparator.comparing(PrintStation::stationId)).toList(); }
    @Override public Optional<PrintStation> station(String id) { return Optional.ofNullable(stations.get(id)); }
    @Override public synchronized PrintStation saveStation(PrintStation station) { stations.put(station.stationId(), station); return station; }
    @Override public List<PrintQueue> queues() { return queues.values().stream().sorted(Comparator.comparing(PrintQueue::stationId).thenComparing(PrintQueue::queueId)).toList(); }
    @Override public synchronized PrintQueue saveQueue(PrintQueue queue) { queues.put(queue.stationId() + "|" + queue.queueId(), queue); return queue; }
    @Override public Optional<PrintBatch> batchByRequest(String requestId) { return Optional.ofNullable(requests.get(requestId)).flatMap(this::batch); }
    @Override public Optional<PrintBatch> batch(String id) { return Optional.ofNullable(batches.get(id)).map(batch -> batch.withJobs(jobs.values().stream().filter(job -> id.equals(job.batchId())).sorted(Comparator.comparing(PrintTargetJob::id)).toList())); }
    @Override public synchronized PrintBatch insertBatch(PrintBatch batch) {
        PrintBatch existing = batchByRequest(batch.requestId()).orElse(null);
        if (existing != null) return existing;
        batches.put(batch.id(), batch.withJobs(List.of()));
        batch.jobs().forEach(job -> jobs.put(job.id(), job));
        requests.put(batch.requestId(), batch.id());
        return batch(batch.id()).orElseThrow();
    }
    @Override public Optional<PrintTargetJob> job(String id) { return Optional.ofNullable(jobs.get(id)); }
    @Override public synchronized Optional<PrintTargetJob> claim(String stationId, String leaseUntil, String now) {
        PrintTargetJob pending = jobs.values().stream().filter(job -> job.status() == PrintJobStatus.PENDING
                && stationId.equals(job.targetStationId())).min(Comparator.comparing(PrintTargetJob::createdAt).thenComparing(PrintTargetJob::id)).orElse(null);
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
}
