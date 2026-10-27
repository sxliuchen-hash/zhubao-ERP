package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.FinanceCommands.ReceiptAllocationCommand;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceiptAllocationOperation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationFollowUpTest {

    @Test
    void existingPrepaymentCanBeAllocatedReplayedAndNotOverused() {
        InMemoryFinanceRepository repository = new InMemoryFinanceRepository();
        repository.saveReceivable(new ReceivableLine("AR-1", 1L, "客户", "2026-10", "FIXTURE", "FIX-AR-1", "MD-1", "ZD-1",
                "", BigDecimal.ONE, ChargeUnit.PIECE, BigDecimal.ONE, new BigDecimal("80.00"), "", "夹具", "财务", "2026-10-01T00:00:00"));
        repository.saveReceivable(new ReceivableLine("AR-2", 1L, "客户", "2026-10", "FIXTURE", "FIX-AR-2", "MD-1", "ZD-2",
                "", BigDecimal.ONE, ChargeUnit.PIECE, BigDecimal.ONE, new BigDecimal("50.00"), "", "夹具", "财务", "2026-10-01T00:00:00"));
        repository.registerReceipt(new CustomerReceipt("RC-1", 1L, "客户", "2026-10", new BigDecimal("100.00"), "2026-10-02",
                List.of(), "财务", "2026-10-02T00:00:00"), "receipt-prepay-01");
        FinanceBillingService service = new FinanceBillingService(repository, null, new StockLedgerService(new InMemoryStockLedgerRepository()));
        ReceiptAllocationOperation first = service.allocateExisting("RC-1", "alloc-0001", ReceiptAllocationOperation.ALLOCATE, "后续核销",
                List.of(new ReceiptAllocationCommand("AR-1", new BigDecimal("80.00"))), "财务");
        ReceiptAllocationOperation replay = service.allocateExisting("RC-1", "alloc-0001", ReceiptAllocationOperation.ALLOCATE, "后续核销",
                List.of(new ReceiptAllocationCommand("AR-1", new BigDecimal("80.00"))), "财务");
        assertThat(replay.code()).isEqualTo(first.code());
        assertThat(service.currentOpen(repository.findReceivableByCode("AR-1").orElseThrow())).isEqualByComparingTo("0.00");
        service.allocateExisting("RC-1", "alloc-0002", ReceiptAllocationOperation.ALLOCATE, "第二单",
                List.of(new ReceiptAllocationCommand("AR-2", new BigDecimal("20.00"))), "财务");
        assertThatThrownBy(() -> service.allocateExisting("RC-1", "alloc-0003", ReceiptAllocationOperation.ALLOCATE, "超额",
                List.of(new ReceiptAllocationCommand("AR-2", new BigDecimal("1.00"))), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过收款金额");
        assertThatThrownBy(() -> service.allocateExisting("RC-1", "alloc-0001", ReceiptAllocationOperation.ALLOCATE, "改内容",
                List.of(new ReceiptAllocationCommand("AR-2", new BigDecimal("80.00"))), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(repository.listAllocationOperations()).hasSize(2);
        assertThat(service.statement(1L, "").prepaymentAmount()).isEqualByComparingTo("0.00");
        assertThat(service.statement(1L, "").outstandingAmount()).isEqualByComparingTo("30.00");
    }
}
