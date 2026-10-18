package com.zhubao.erp.label.domain;

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

    List<PrintJob> listByBusinessAny(String businessCode);

    void recycle(String id, String operator, String at, String batch, String reason);

    int recycleByBusinessCode(String businessCode, String operator, String at, String batch, String reason);

    void restore(String id);

    void restoreBatch(String batch);

    Optional<PrintRecycle> recycleMark(String id);

    List<PrintRecycle> recycled();

    PrintJob replaceCopies(String id, int copies, String stationId, String updatedAt);
}
