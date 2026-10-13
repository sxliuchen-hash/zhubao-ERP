package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.label-template.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryPrintJobRepository implements PrintJobRepository {

    private final ConcurrentHashMap<String, PrintJob> jobs = new ConcurrentHashMap<>();

    @Override
    public Optional<PrintJob> findById(String id) {
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
        return List.copyOf(jobs.values());
    }
}
