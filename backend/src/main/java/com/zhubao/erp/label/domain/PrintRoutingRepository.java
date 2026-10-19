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

    /**
     * 新建原始批次。先锁定该业务单尚未回收的目标任务；失败或不确定且未核纸时拒绝，避免再生成可领取任务。
     * 改派不走这里，以免取消原任务和插入后继任务被这次检查拆开。
     */
    PrintBatch insertOriginalBatch(PrintBatch batch);
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

    /** 按任务编号顺序锁定这些业务单的目标任务，须晚于母单和子单。 */
    void lockByBusinessCodes(List<String> businessCodes, WorkSession session);
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
