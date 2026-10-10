package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户范围台账过滤。JDBC 使用 H2 的 MySQL 兼容模式，不能当作真实 MySQL 验收。
 */
class CustomerLedgerRangeTest {

    @Test
    void memoryAndH2KeepTheSameCustomerAndMotherRange() throws Exception {
        StockLedgerRepository memory = StockRepositoryFixture.create(false);
        StockLedgerRepository h2 = StockRepositoryFixture.create(true);
        save(memory);
        save(h2);

        assertThat(h2.findLedgersByCustomers(Set.of(7001L), Set.of())).extracting(StockLedgerEntry::code)
                .containsExactlyElementsOf(memory.findLedgersByCustomers(Set.of(7001L), Set.of()).stream().map(StockLedgerEntry::code).toList());
        assertThat(h2.findLedgersByCustomers(Set.of(), Set.of("MD-B"))).extracting(StockLedgerEntry::code)
                .containsExactly("STK-B");
        assertThat(memory.findLedgersByCustomers(Set.of(), Set.of("MD-B"))).extracting(StockLedgerEntry::code)
                .containsExactly("STK-B");
        assertThat(h2.findLedgersByCustomers(Set.of(), Set.of())).isEmpty();
        assertThat(memory.findLedgersByCustomers(Set.of(), Set.of())).isEmpty();
        assertThat(h2.findAllLedgers()).extracting(StockLedgerEntry::code).containsExactly("STK-A", "STK-B");
    }

    private static void save(StockLedgerRepository repository) {
        repository.save(entry("STK-A", "2026-01-01T00:00:00Z", 7001L, "MD-A"));
        repository.save(entry("STK-B", "2026-01-01T00:00:01Z", 7002L, "MD-B"));
    }

    private static StockLedgerEntry entry(String code, String time, Long customerId, String mother) {
        StockDimension to = new StockDimension(OwnerType.CUSTOMER, customerId, customerId, mother, null, "MAT-REAL", "RAW", StockStatus.RAW_WAITING);
        return new StockLedgerEntry(code, Instant.parse(time), "inbound_confirm", mother, null, to, BigDecimal.ONE, BigDecimal.ONE, "");
    }
}
