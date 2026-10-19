package com.zhubao.erp.label.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface PrintJobRepository {

    Optional<PrintJob> findById(String id);

    Optional<PrintJob> findByRequestId(String requestId);

    PrintJob save(PrintJob job);

    /**
     * 按请求编号插入。已有同一请求时返回库内真实行，不覆盖状态、租约或快照。
     */
    PrintJob insertIfAbsent(PrintJob job);

    /** 原子领取一条待打印任务。没有可领任务时返回空。 */
    Optional<PrintJob> claim(String stationId, String leaseUntil, String now);

    List<PrintJob> list();

    Optional<PrintJob> findAny(String id);

    /** 在当前工作单元里锁定读取旧打印任务。 */
    Optional<PrintJob> lockAny(String id, WorkSession session);

    List<PrintJob> listByBusinessAny(String businessCode);

    void recycle(String id, String operator, String at, String batch, String reason);
    void recycle(String id, String operator, String at, String batch, String reason, WorkSession session);

    int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason);
    int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason, WorkSession session);

    void restore(String id);
    void restore(String id, WorkSession session);

    void restoreBatch(String batch);
    void restoreBatch(String batch, WorkSession session);

    Optional<PrintRecycle> recycleMark(String id);

    List<PrintRecycle> recycled();

    /** 仅改正未领取任务，并与调整记录共用工作单元。 */
    PrintJob replaceCopies(String id, int copies, String stationId, String updatedAt, WorkSession session);
}
