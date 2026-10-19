package com.zhubao.erp.label.domain;

import com.zhubao.erp.work.domain.WorkSession;

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
    Optional<PrintBatch> rerouteJob(String originalId, PrintJobStatus expected, PrintBatch replacement, String reason, String now);
    Optional<PrintTargetJob> job(String jobId);
    Optional<PrintTargetJob> claim(String stationId, String leaseUntil, String now);
    int expireLeases(String now);
    PrintTargetJob saveJob(PrintTargetJob job);
    Optional<PrintTargetJob> saveJobIfStatus(PrintTargetJob job, PrintJobStatus expected);
    Optional<PrintTargetJob> savePaperCheckIfBlank(PrintTargetJob job, PrintJobStatus expected);
    Optional<PrintTargetJob> jobAny(String jobId);
    /** 在当前工作单元里锁定读取目标任务。 */
    Optional<PrintTargetJob> lockJobAny(String jobId, WorkSession session);
    List<PrintTargetJob> jobsForBusinessAny(String businessCode);
    void recycleJob(String id, String operator, String at, String batch, String reason);
    void recycleJob(String id, String operator, String at, String batch, String reason, WorkSession session);
    int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason);
    int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason, WorkSession session);
    void restoreJob(String id);
    void restoreJob(String id, WorkSession session);
    void restoreBatch(String batch);
    void restoreBatch(String batch, WorkSession session);
    Optional<PrintRecycle> jobRecycle(String id);
    List<PrintRecycle> recycledJobs();
    /** 仅改正未领取目标任务，并与调整记录共用工作单元。 */
    PrintTargetJob replaceTarget(String id, int copies, String stationId, String queueId, String updatedAt,
                                 WorkSession session);
}
