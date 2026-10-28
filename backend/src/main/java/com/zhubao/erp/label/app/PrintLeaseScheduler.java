package com.zhubao.erp.label.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 租约到期后及时改为不确定，不依赖下一次领取。 */
@Component
public class PrintLeaseScheduler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PrintJobService jobs;
    private final PrintBatchService batches;

    public PrintLeaseScheduler(PrintJobService jobs, PrintBatchService batches) {
        this.jobs = jobs;
        this.batches = batches;
    }

    @Scheduled(fixedDelay = 15000)
    public void expire() {
        String now = TIME.format(LocalDateTime.now());
        batches.expireLeases(now);
        jobs.expireLeases(now);
    }
}