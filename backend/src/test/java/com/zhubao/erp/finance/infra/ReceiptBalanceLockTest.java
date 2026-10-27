package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptBalanceLockTest {

    @Test
    void replaySucceedsAfterBalanceDropsAndANewOverAllocationIsRejected() {
        InMemoryFinanceRepository repository = new InMemoryFinanceRepository();
        repository.saveReceivable(new ReceivableLine(
                "AR-1", 1L, "客户", "2026-10", "MANUAL", "MANUAL|AR-1", "MD-1", "MD-1", "",
                BigDecimal.ONE, ChargeUnit.JOB, new BigDecimal("100.0000"), new BigDecimal("100.00"),
                "", "", "财务", "2026-10-27T00:00:00"));
        CustomerReceipt first = receipt("RC-1", "80.00", "AR-1", "80.00");
        assertThat(repository.registerReceipt(first, "receipt-key-0001").code()).isEqualTo("RC-1");
        assertThatThrownBy(() -> repository.registerReceipt(receipt("RC-2", "30.00", "AR-1", "30.00"), "receipt-key-0002"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过尚未核销的余额");
        CustomerReceipt replay = receipt("RC-9", "80.00", "AR-1", "80.00");
        assertThat(repository.registerReceipt(replay, "receipt-key-0001").code()).isEqualTo("RC-1");
        assertThat(repository.listReceipts()).hasSize(1);
    }

    private static CustomerReceipt receipt(String code, String amount, String receivable, String allocated) {
        return new CustomerReceipt(code, 1L, "客户", "2026-10", new BigDecimal(amount), "2026-10-27",
                List.of(new ReceiptAllocation(receivable, new BigDecimal(allocated))), "财务", "2026-10-27T01:00:00");
    }
}
