package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcStockLedgerRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcStockLedgerRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = StockRepositoryFixture.jdbcDataSource();
        repository = new JdbcStockLedgerRepository(dataSource);
    }

    @Test
    void persistsLedgerEntriesAndRebuildsBalances() {
        StockLedgerService service = new StockLedgerService(repository);
        StockDimension raw = dimension(StockStatus.RAW_WAITING, "RAW-CUSTOMER");
        StockDimension wip = dimension(StockStatus.WIP, "WIP-LINE-01");

        service.transfer(new StockTransferCommand("inbound", "MD-20260706-001", null, raw, bd("10"), bd("35.200"), "inbound"));
        service.transfer(new StockTransferCommand("split_work_order", "ZD-20260706-001", raw, wip, bd("4"), bd("14.080"), "split"));

        JdbcStockLedgerRepository reloaded = new JdbcStockLedgerRepository(dataSource);
        StockBalance rawBalance = reloaded.findBalance(raw);
        StockBalance wipBalance = reloaded.findBalance(wip);

        assertThat(reloaded.findAllLedgers()).hasSize(2);
        assertThat(reloaded.findAllBalances()).hasSize(2);
        assertThat(rawBalance.quantity()).isEqualByComparingTo("6");
        assertThat(rawBalance.weight()).isEqualByComparingTo("21.120");
        assertThat(wipBalance.quantity()).isEqualByComparingTo("4");
        assertThat(wipBalance.weight()).isEqualByComparingTo("14.080");
    }

    @Test
    void rejectsLedgerThatChangesBusinessDimensionDuringTransfer() {
        StockDimension from = dimension(StockStatus.RAW_WAITING, "RAW-CUSTOMER");
        StockDimension to = new StockDimension(
                OwnerType.COMPANY,
                null,
                null,
                from.motherOrderCode(),
                from.workOrderCode(),
                from.materialCode(),
                "COMPANY-WIP",
                StockStatus.WIP
        );

        StockLedgerEntry invalid = new StockLedgerEntry(
                "STK-INVALID",
                Instant.now(),
                "manual",
                "DOC-001",
                from,
                to,
                bd("1"),
                bd("1.000"),
                "invalid transfer"
        );

        assertThatThrownBy(() -> repository.save(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能改变权属");
    }

    @Test
    void migrationBackfillsOnlyHistoricalTransfersAndPreservesNullSources() throws SQLException {
        JdbcDataSource legacy = StockRepositoryFixture.legacyJdbcDataSource();
        try (Connection connection = legacy.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    insert into inv_stock_ledger (
                        code, biz_time, document_type, document_code, owner_type, owner_customer_id,
                        customer_id, mother_order_code, work_order_code, material_code, quantity, weight,
                        from_warehouse_code, to_warehouse_code, from_stock_status, to_stock_status
                    ) values
                    ('OLD-INBOUND', current_timestamp, 'inbound_confirm', 'MD-OLD', 'CUSTOMER', 1001,
                     1001, 'MD-OLD', 'ZD-OLD', 'AU9999', 10, 20, null, 'RAW', null, 'RAW_WAITING'),
                    ('OLD-TRANSFER', current_timestamp, 'production_report', 'ZD-OLD', 'CUSTOMER', 1001,
                     1001, 'MD-OLD', 'ZD-OLD', 'AU9999', 4, 8, 'RAW', 'SEMI', 'WIP', 'SEMI_FINISHED'),
                    ('OLD-RAW-TRANSFER', current_timestamp, 'manual', 'MD-OLD', 'CUSTOMER', 1001,
                     1001, 'MD-OLD', null, 'AU9999', 1, 2, 'RAW', 'RAW2', 'RAW_WAITING', 'RAW_WAITING')
                    """);
        }

        StockRepositoryFixture.migrateSourceWorkOrder(legacy);

        var ledgers = new JdbcStockLedgerRepository(legacy).findAllLedgers();
        assertThat(ledgers).filteredOn(entry -> "OLD-INBOUND".equals(entry.code())).singleElement()
                .satisfies(entry -> assertThat(entry.from()).isNull());
        assertThat(ledgers).filteredOn(entry -> "OLD-TRANSFER".equals(entry.code())).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.from().workOrderCode()).isEqualTo("ZD-OLD");
                    assertThat(entry.to().workOrderCode()).isEqualTo("ZD-OLD");
                });
        assertThat(ledgers).filteredOn(entry -> "OLD-RAW-TRANSFER".equals(entry.code())).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.from().workOrderCode()).isNull();
                    assertThat(entry.to().workOrderCode()).isNull();
                });
    }

    private StockDimension dimension(StockStatus status, String warehouseCode) {
        return new StockDimension(
                OwnerType.CUSTOMER,
                1001L,
                1001L,
                "MD-20260706-001",
                "ZD-20260706-001",
                "MAT-GOLD",
                warehouseCode,
                status
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
