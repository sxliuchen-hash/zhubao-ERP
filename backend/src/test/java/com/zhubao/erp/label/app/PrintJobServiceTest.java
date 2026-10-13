package com.zhubao.erp.label.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.infra.InMemoryPrintJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintJobServiceTest {

    private InMemoryPrintJobRepository repository;
    private PrintJobService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPrintJobRepository();
        service = new PrintJobService(repository, "station-secret");
    }

    @Test
    void replayKeepsOneJobAndSpooledDoesNotMeanPrinted() {
        PrintJob first = service.create("print-job-0001", "MD-1", "FLOW", 1, "何琳");
        PrintJob second = service.create("print-job-0001", "MD-1", "FLOW", 1, "何琳");
        assertThat(second.id()).isEqualTo(first.id());
        assertThatThrownBy(() -> service.create("print-job-0001", "MD-1", "FLOW", 2, "何琳"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");

        PrintJob leased = service.claim("STATION-1", "station-secret");
        PrintJob spooled = service.report(leased.id(), "STATION-1", "station-secret", "SPOOLED", "");
        assertThat(spooled.status()).isEqualTo(PrintJobStatus.SPOOLED);
        assertThat(spooled.status()).isNotEqualTo(PrintJobStatus.PENDING);
    }

    @Test
    void expiredLeaseBecomesUncertainAndIsNotClaimedAgain() {
        PrintJob created = service.create("print-job-0002", "MD-2", "FLOW", 1, "何琳");
        PrintJob leased = service.claim("STATION-1", "station-secret");
        repository.save(new PrintJob(
                leased.id(), leased.requestId(), leased.businessCode(), leased.templateCode(), leased.copies(),
                leased.contentSnapshot(), PrintJobStatus.LEASED, leased.stationId(),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().minusMinutes(5)),
                "", "", "", leased.createdBy(), leased.createdAt(), leased.updatedAt()
        ));
        assertThat(service.expireLeases(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))).isEqualTo(1);
        assertThat(service.get(created.id()).status()).isEqualTo(PrintJobStatus.UNCERTAIN);
        assertThatThrownBy(() -> service.claim("STATION-1", "station-secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有待打印任务");

        PrintJob retry = service.retry(created.id(), "print-retry-001", "核对后纸上没有", "何琳");
        assertThat(retry.id()).isNotEqualTo(created.id());
        assertThat(retry.predecessorId()).isEqualTo(created.id());
        assertThat(service.get(created.id()).status()).isEqualTo(PrintJobStatus.UNCERTAIN);
        assertThat(retry.contentSnapshot()).isEqualTo(created.contentSnapshot());
    }

    @Test
    void getExpiresLeaseWithoutWaitingForTheNextClaim() {
        PrintJob created = service.create("print-job-0003", "MD-3", "FLOW", 1, "何琳");
        PrintJob leased = service.claim("STATION-1", "station-secret");
        repository.save(new PrintJob(
                leased.id(), leased.requestId(), leased.businessCode(), leased.templateCode(), leased.copies(),
                leased.contentSnapshot(), PrintJobStatus.LEASED, leased.stationId(),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().minusMinutes(5)),
                "", "", "", leased.createdBy(), leased.createdAt(), leased.updatedAt()
        ));
        assertThat(service.get(created.id()).status()).isEqualTo(PrintJobStatus.UNCERTAIN);
        assertThat(service.list()).allMatch(job -> job.status() != PrintJobStatus.LEASED);
        assertThatThrownBy(() -> service.claim("STATION-1", "station-secret"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentSameRequestCreatesOneJob() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PrintJob> first = pool.submit(() -> {
                start.await();
                return service.create("print-job-race", "MD-RACE", "FLOW", 1, "何琳");
            });
            Future<PrintJob> second = pool.submit(() -> {
                start.await();
                return service.create("print-job-race", "MD-RACE", "FLOW", 1, "何琳");
            });
            start.countDown();
            assertThat(first.get().id()).isEqualTo(second.get().id());
            assertThat(repository.list()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
