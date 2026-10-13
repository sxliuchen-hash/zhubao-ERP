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

    public PrintLeaseScheduler(PrintJobService jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelay = 15000)
    public void expire() {
        jobs.expireLeases(TIME.format(LocalDateTime.now()));
    }
}