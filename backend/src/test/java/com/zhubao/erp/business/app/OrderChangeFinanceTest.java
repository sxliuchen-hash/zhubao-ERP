package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryOrderChangeRepository;
import com.zhubao.erp.finance.app.FinanceBillingService;
import com.zhubao.erp.finance.app.FinancePayrollService;
import com.zhubao.erp.finance.app.OrderChangePostingService;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceCommands.PayrollRunCommand;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.label.app.PrintJobService;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.infra.InMemoryPrintJobRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderChangeFinanceTest {

    private InMemoryBusinessDocumentRepository documents;
    private InMemoryFinanceRepository finance;
    private InMemoryOrderChangeRepository changes;
    private OrderChangeService service;
    private AuthUserAccount financeUser;

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        finance = new InMemoryFinanceRepository();
        changes = new InMemoryOrderChangeRepository();
        StockLedgerService ledger = new StockLedgerService(new InMemoryStockLedgerRepository());
        new BusinessOperationService(ledger, null, documents).confirmInbound(new ConfirmInboundCommand(
                "MD-CHANGE-001", 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF-CHANGE-002", "",
                "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "原单",
                "内壁刻字", "2026-10-20", "恒信珠宝"
        ));
        documents.saveProcessStationHandoff(new ProcessStationHandoff(
                "MD-CHANGE-001", "雕刻", "雕刻工", "STK-1", "2026-10-09T10:00:00"
        ));
        finance.saveReceivable(new ReceivableLine(
                "FIN-1", 1001L, "恒信珠宝", "2026-10", "PROCESS", "PROCESS|MD-CHANGE-001|雕刻",
                "MD-CHANGE-001", "MD-CHANGE-001", "雕刻", BigDecimal.ONE, ChargeUnit.JOB,
                new BigDecimal("100.00"), new BigDecimal("100.00"), "", "原账单", "财务", "2026-10-09T10:00:00"
        ));
        FinanceBillingService billing = new FinanceBillingService(finance, documents, ledger);
        service = new OrderChangeService(
                changes,
                documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()),
                new OrderChangePostingService(finance, billing, documents)
        );
        financeUser = account("财务员", RoleCode.FINANCE_STATISTICIAN);
    }

    @Test
    void applyPostsReductionAndPayrollAdjustmentWithoutReplacingHistory() {
        AuthUserAccount receiver = account("收货员", RoleCode.RECEIVER);
        var draft = service.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-money-001", receiver);
        service.submitFinance(draft.id(), receiver);
        service.confirmFinance(draft.id(), false, new BigDecimal("70"), new BigDecimal("8"),
                new BigDecimal("-30.00"), new BigDecimal("-8.00"), "FIN-1", "雕刻", "雕刻工", "减额", financeUser);
        service.apply(draft.id(), "change-money-apply", receiver);
        service.apply(draft.id(), "change-money-apply", receiver);

        assertThat(finance.findReceivableByCode("FIN-1").orElseThrow().amount()).isEqualByComparingTo("100.00");
        assertThat(finance.listReceivables().stream().filter(line -> "FIN-1".equals(line.reversesCode()))).hasSize(1);
        assertThat(finance.findReceivableBySourceKey("CHANGE-RED|" + draft.id())).isPresent();
        assertThat(finance.listPayrollLines().stream().filter(line -> line.sourceKey().equals("PAY-ADJ|" + draft.id())))
                .singleElement()
                .satisfies(line -> assertThat(line.netAmount()).isEqualByComparingTo("-8.00"));
        assertThat(documents.listProcessStationHandoffs("MD-CHANGE-001")).hasSize(1);

        FinancePayrollService payroll = new FinancePayrollService(
                finance, documents, new ApprovalService(new InMemoryApprovalTaskRepository()));
        payroll.generate(new PayrollRunCommand(YearMonth.now().toString()));
        assertThat(finance.listPayrollLines().stream().filter(line -> line.sourceKey().startsWith("PAY-ADJ|"))).hasSize(1);

        PrintJobService printing = new PrintJobService(new InMemoryPrintJobRepository(), "station-secret", changes);
        PrintJob printed = printing.create("print-change-001", "MD-CHANGE-001", "FLOW", 1, "何琳");
        assertThat(printed.contentSnapshot()).endsWith("|V1");
        PrintJob reprinted = printing.retry(printed.id(), "print-change-002", "同版补打", "何琳");
        assertThat(reprinted.contentSnapshot()).isEqualTo(printed.contentSnapshot());
        assertThat(reprinted.id()).isNotEqualTo(printed.id());
    }

    @Test
    void increaseUsesAChangeSourceKey() {
        AuthUserAccount receiver = account("收货员", RoleCode.RECEIVER);
        var draft = service.draft("MD-CHANGE-001", "", "加镶嵌", "2026-11-01", "change-plus-0001", receiver);
        service.submitFinance(draft.id(), receiver);
        service.confirmFinance(draft.id(), false, new BigDecimal("120"), new BigDecimal("0"),
                new BigDecimal("20.00"), BigDecimal.ZERO, "", "镶嵌", "", "加价", financeUser);
        service.apply(draft.id(), "change-plus-apply", receiver);
        assertThat(finance.findReceivableBySourceKey("CHANGE|" + draft.id() + "|MD-CHANGE-001|镶嵌"))
                .get()
                .extracting(ReceivableLine::amount)
                .isEqualTo(new BigDecimal("20.00"));
        assertThat(finance.findReceivableBySourceKey("PROCESS|MD-CHANGE-001|雕刻")).isPresent();
    }

    @Test
    void wrongReceivableIsRejectedAndFailureRollsBackTheAdjustment() {
        finance.saveReceivable(new ReceivableLine(
                "FIN-OTHER", 2002L, "另一客户", "2026-10", "PROCESS", "PROCESS|MD-OTHER|雕刻",
                "MD-OTHER", "MD-OTHER", "雕刻", BigDecimal.ONE, ChargeUnit.JOB,
                new BigDecimal("80.00"), new BigDecimal("80.00"), "", "别人的账", "财务", "2026-10-09T10:00:00"
        ));
        AuthUserAccount receiver = account("收货员", RoleCode.RECEIVER);
        var wrong = service.draft("MD-CHANGE-001", "", "错账", "2026-11-01", "change-wrong-001", receiver);
        service.submitFinance(wrong.id(), receiver);
        service.confirmFinance(wrong.id(), false, new BigDecimal("70"), new BigDecimal("8"),
                new BigDecimal("-10.00"), BigDecimal.ZERO, "FIN-OTHER", "雕刻", "", "错账", financeUser);
        assertThatThrownBy(() -> service.apply(wrong.id(), "change-wrong-apply", receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该客户");
        assertThat(finance.findReceivableBySourceKey("CHANGE-RED|" + wrong.id())).isEmpty();
        assertThat(changes.findById(wrong.id()).orElseThrow().status()).isEqualTo(com.zhubao.erp.business.domain.OrderChangeStatus.FINANCE_CONFIRMED);

        CommandResultProbe probe = () -> {
            throw new IllegalStateException("结果写入失败");
        };
        OrderChangeService failing = new OrderChangeService(
                changes, documents, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), probe),
                new OrderChangePostingService(finance, new FinanceBillingService(finance, documents, new StockLedgerService(new InMemoryStockLedgerRepository())), documents)
        );
        var draft = failing.draft("MD-CHANGE-001", "", "回滚", "2026-11-02", "change-rollback-001", receiver);
        failing.submitFinance(draft.id(), receiver);
        failing.confirmFinance(draft.id(), false, new BigDecimal("70"), new BigDecimal("8"),
                new BigDecimal("-30.00"), new BigDecimal("-8.00"), "FIN-1", "雕刻", "雕刻工", "回滚", financeUser);
        assertThatThrownBy(() -> failing.apply(draft.id(), "change-rollback-apply", receiver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("结果写入失败");
        assertThat(changes.findById(draft.id()).orElseThrow().status()).isEqualTo(com.zhubao.erp.business.domain.OrderChangeStatus.FINANCE_CONFIRMED);
        assertThat(finance.findReceivableByCode("FIN-1").orElseThrow().amount()).isEqualByComparingTo("100.00");
        assertThat(finance.findReceivableBySourceKey("CHANGE-RED|" + draft.id())).isEmpty();
        assertThat(finance.listPayrollLines().stream().filter(line -> ("PAY-ADJ|" + draft.id()).equals(line.sourceKey()))).isEmpty();
    }

    @Test
    void concurrentReplayPostsTheReductionOnce() throws Exception {
        AuthUserAccount receiver = account("收货员", RoleCode.RECEIVER);
        var draft = service.draft("MD-CHANGE-001", "", "并发", "2026-11-01", "change-race-001", receiver);
        service.submitFinance(draft.id(), receiver);
        service.confirmFinance(draft.id(), false, new BigDecimal("70"), new BigDecimal("8"),
                new BigDecimal("-30.00"), BigDecimal.ZERO, "FIN-1", "雕刻", "", "并发", financeUser);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> applyTogether(draft.id(), receiver, ready, start));
            Future<?> second = pool.submit(() -> applyTogether(draft.id(), receiver, ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(finance.listReceivables().stream().filter(line -> ("CHANGE-RED|" + draft.id()).equals(line.sourceKey()))).hasSize(1);
        assertThat(changes.findById(draft.id()).orElseThrow().status()).isEqualTo(com.zhubao.erp.business.domain.OrderChangeStatus.APPLIED);
    }

    private void applyTogether(String id, AuthUserAccount receiver, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        service.apply(id, "change-race-apply", receiver);
    }

    private AuthUserAccount account(String operator, RoleCode role) {
        return new AuthUserAccount(
                operator, "hash", operator, role, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of()
        );
    }
}
