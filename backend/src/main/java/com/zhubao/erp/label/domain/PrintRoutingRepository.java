package com.zhubao.erp.label.domain;

import java.util.List;
import java.util.Optional;

public interface PrintRoutingRepository {
    List<PrintStation> stations();
    Optional<PrintStation> station(String stationId);
    PrintStation saveStation(PrintStation station);
    List<PrintQueue> queues();
    PrintQueue saveQueue(PrintQueue queue);
    Optional<PrintBatch> batchByRequest(String requestId);
    Optional<PrintBatch> batch(String batchId);
    PrintBatch insertBatch(PrintBatch batch);
    Optional<PrintTargetJob> job(String jobId);
    Optional<PrintTargetJob> claim(String stationId, String leaseUntil, String now);
    int expireLeases(String now);
    PrintTargetJob saveJob(PrintTargetJob job);
    Optional<PrintTargetJob> saveJobIfStatus(PrintTargetJob job, PrintJobStatus expected);
}
