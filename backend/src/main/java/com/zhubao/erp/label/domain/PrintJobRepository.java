package com.zhubao.erp.label.domain;

import java.util.List;
import java.util.Optional;

public interface PrintJobRepository {

    Optional<PrintJob> findById(String id);

    Optional<PrintJob> findByRequestId(String requestId);

    PrintJob save(PrintJob job);

    /** 原子领取一条待打印任务。没有可领任务时返回空。 */
    Optional<PrintJob> claim(String stationId, String leaseUntil, String now);

    List<PrintJob> list();
}
