package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockLedgerServiceTest {

    private InMemoryStockLedgerRepository repository;
    private StockLedgerService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryStockLedgerRepository();
        service = new StockLedgerService(repository);
    }

    @Test
    void inboundAndTransferKeepCustomerOwnedStockSeparated() {
        StockDimension raw = dimension(StockStatus.RAW_WAITING, "RAW");
        StockDimension wip = dimension(StockStatus.WIP, "WIP");

        service.transfer(new StockTransferCommand("inbound", "CC-001", null, raw, bd("10"), bd("35.200"), "客户来料入库"));
        service.transfer(new StockTransferCommand("split_work_order", "WO-001", raw, wip, bd("4"), bd("14.080"), "拆分子工单"));

        StockBalance rawBalance = repository.findBalance(raw);
        StockBalance wipBalance = repository.findBalance(wip);

        assertThat(rawBalance.quantity()).isEqualByComparingTo("6");
        assertThat(rawBalance.weight()).isEqualByComparingTo("21.120");
        assertThat(wipBalance.quantity()).isEqualByComparingTo("4");
        assertThat(wipBalance.weight()).isEqualByComparingTo("14.080");
        assertThat(wipBalance.dimension().ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(repository.findAllLedgers()).hasSize(2);
    }

    @Test
    void rejectsTransferWhenSourceQuantityIsInsufficient() {
        StockDimension raw = dimension(StockStatus.RAW_WAITING, "RAW");
        StockDimension wip = dimension(StockStatus.WIP, "WIP");
        service.transfer(new StockTransferCommand("inbound", "CC-001", null, raw, bd("1"), bd("3.500"), null));

        StockTransferCommand command = new StockTransferCommand("split_work_order", "WO-001", raw, wip, bd("2"), bd("7.000"), null);

        assertThatThrownBy(() -> service.transfer(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量不足");
    }

    private StockDimension dimension(StockStatus status, String warehouseCode) {
        return new StockDimension(
                OwnerType.CUSTOMER,
                1001L,
                1001L,
                "CC-20260706-001-JL",
                "CC-20260706-001-JL-ZD001",
                "MAT-GOLD",
                warehouseCode,
                status
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
