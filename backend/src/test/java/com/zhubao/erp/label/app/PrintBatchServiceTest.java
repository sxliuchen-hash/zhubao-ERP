package com.zhubao.erp.label.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTarget;
import com.zhubao.erp.label.infra.InMemoryPrintJobRepository;
import com.zhubao.erp.label.infra.InMemoryPrintRoutingRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintBatchServiceTest {
    private final InMemoryPrintRoutingRepository repository = new InMemoryPrintRoutingRepository();
    private final PrintBatchService service = new PrintBatchService(repository,
            new PrintJobService(new InMemoryPrintJobRepository(), "legacy-token"));
    private final AuthUserAccount account = new AuthUserAccount("operator", "hash", "操作员",
            RoleCode.WAREHOUSE_KEEPER, DataScope.ALL, true, false, Instant.now(), 0,
            null, List.of(), false, false, List.of());

    @Test
    void 同工位多队列与跨工位隔离且批次回放不增单() throws Exception {
        String tokenA = service.saveStation(new PrintStation("A", "收货", true, "", "")).token();
        String tokenB = service.saveStation(new PrintStation("B", "包装", true, "", "")).token();
        service.saveQueue(new PrintQueue("A", "Q1", "主打印机", "ALL", 40, 30, true, true));
        service.saveQueue(new PrintQueue("A", "Q2", "备用打印机", "ALL", 40, 30, true, false));
        service.saveQueue(new PrintQueue("B", "Q1", "包装打印机", "ALL", 40, 30, true, true));
        List<PrintTarget> targets = List.of(new PrintTarget("A", "Q1"), new PrintTarget("A", "Q2"),
                new PrintTarget("B", "Q1"), new PrintTarget("A", "Q1"));
        PrintBatch first = service.create("batch-request-0001", "MD-1", "FLOW", 2, targets, account);
        assertThat(first.jobs()).hasSize(3);
        assertThat(service.create("batch-request-0001", "MD-1", "FLOW", 2, targets, account).id()).isEqualTo(first.id());
        assertThat(repository.batch(first.id()).orElseThrow().jobs()).hasSize(3);
        assertThatThrownBy(() -> service.create("batch-request-0001", "MD-1", "FLOW", 3, targets, account))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不一致");
        assertThatThrownBy(() -> service.claim("B", tokenA)).isInstanceOf(PermissionDeniedException.class);
        PrintBatchService.ClaimedPrintJob claimedB = service.claim("B", tokenB);
        assertThat(claimedB.job().targetStationId()).isEqualTo("B");
        assertThat(claimedB.job().targetQueueId()).isEqualTo("Q1");
        assertThatThrownBy(() -> service.report(claimedB.job().id(), "A", tokenA, "SPOOLED", ""))
                .isInstanceOf(BusinessException.class);
        var failed = service.report(claimedB.job().id(), "B", tokenB, "FAILED", "本机队列不可用");
        assertThat(failed.status()).isEqualTo(PrintJobStatus.FAILED);
        assertThat(failed.failureReason()).contains("队列不可用");
        assertThat(service.paperCheck(failed.id(), "未出纸", account).paperCheckResult()).isEqualTo("未出纸");
        assertThat(service.claim("A", tokenA).job().targetQueueId()).isIn("Q1", "Q2");
        assertThat(service.claim("A", tokenA).job().targetQueueId()).isIn("Q1", "Q2");
        assertThatThrownBy(() -> service.claim("A", tokenA)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 并发重试只创建一个批次且队列纸张不匹配被拒绝() throws Exception {
        service.saveStation(new PrintStation("A", "收货", true, "", ""));
        service.saveQueue(new PrintQueue("A", "Q1", "40毫米队列", "ALL", 40, 30, true, true));
        List<PrintTarget> targets = List.of(new PrintTarget("A", "Q1"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return service.create("batch-race-0001", "MD-2", "FLOW", 1, targets, account); });
            var b = pool.submit(() -> { start.await(); return service.create("batch-race-0001", "MD-2", "FLOW", 1, targets, account); });
            start.countDown();
            assertThat(a.get().id()).isEqualTo(b.get().id());
            assertThat(repository.batchByRequest("batch-race-0001").orElseThrow().jobs()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        service.saveQueue(new PrintQueue("A", "Q1", "错误尺寸", "ALL", 50, 30, true, true));
        assertThatThrownBy(() -> service.create("batch-other-0001", "MD-2", "FLOW", 1, targets, account))
                .isInstanceOf(BusinessException.class).hasMessageContaining("纸张规格");
    }

    @Test
    void 租约超时转不确定且不会自动再次领取() {
        String token = service.saveStation(new PrintStation("A", "收货", true, "", "")).token();
        service.saveQueue(new PrintQueue("A", "Q1", "主队列", "ALL", 40, 30, true, true));
        service.create("batch-lease-0001", "MD-3", "FLOW", 1,
                List.of(new PrintTarget("A", "Q1")), account);
        var claimed = service.claim("A", token).job();
        String old = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().minusMinutes(3));
        repository.saveJob(claimed.withStatus(PrintJobStatus.LEASED, "A", old, "", "", old));
        assertThat(service.expireLeases(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))).isEqualTo(1);
        assertThat(repository.job(claimed.id()).orElseThrow().status()).isEqualTo(PrintJobStatus.UNCERTAIN);
        assertThatThrownBy(() -> service.claim("A", token)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 提交后停用队列则目标失败而不送到其他队列() {
        String token = service.saveStation(new PrintStation("A", "收货", true, "", "")).token();
        service.saveQueue(new PrintQueue("A", "Q1", "主队列", "ALL", 40, 30, true, true));
        PrintBatch batch = service.create("batch-disabled-0001", "MD-4", "FLOW", 1,
                List.of(new PrintTarget("A", "Q1")), account);
        service.saveQueue(new PrintQueue("A", "Q1", "主队列", "ALL", 40, 30, false, true));
        assertThatThrownBy(() -> service.claim("A", token)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有待打印任务");
        assertThat(repository.job(batch.jobs().get(0).id()).orElseThrow().status()).isEqualTo(PrintJobStatus.FAILED);
    }
}
