package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.ReceiptEntryReversal;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptEntryReversalTest {

    @Test
    void 内存冲正失败时恢复原余额且不产生报废库存() {
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService service = new StockLedgerService(repository);
        StockDimension raw = raw();
        StockLedgerEntry inbound = service.transfer(new StockTransferCommand(
                "inbound_confirm", "MD-1", null, raw, new BigDecimal("2"), new BigDecimal("5"), "收货"));
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        assertThatThrownBy(() -> unit.execute(call("req-reversal"), session -> {
            service.reverseInbound(new ReceiptEntryReversal(
                    inbound.code(), "MD-1", raw, new BigDecimal("2"), new BigDecimal("5"), "录错冲正"), session);
            throw new IllegalStateException("单据事实写入失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findBalance(raw).quantity()).isEqualByComparingTo("2");
        assertThat(service.ledgers()).hasSize(1);
        assertThat(service.balances()).noneMatch(balance -> balance.dimension().stockStatus() == StockStatus.SCRAP
                || balance.dimension().stockStatus() == StockStatus.FROZEN);
    }

    @Test
    void 数据库冲正扣减原余额且第二次失败整笔回滚() throws Exception {
        var dataSource = StockRepositoryFixture.jdbcDataSource();
        JdbcStockLedgerRepository repository = new JdbcStockLedgerRepository(dataSource);
        StockLedgerService service = new StockLedgerService(repository);
        StockDimension raw = raw();
        StockLedgerEntry inbound = service.transfer(new StockTransferCommand(
                "inbound_confirm", "MD-1", null, raw, new BigDecimal("2"), new BigDecimal("5"), "收货"));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            WorkSession session = new WorkSession() {
                @Override
                public Optional<Connection> connection() {
                    return Optional.of(connection);
                }

                @Override
                public void onRollback(Runnable undo) {
                }
            };
            StockLedgerEntry reversal = service.reverseInbound(new ReceiptEntryReversal(
                    inbound.code(), "MD-1", raw, new BigDecimal("2"), new BigDecimal("5"), "录错冲正"), session);
            assertThat(reversal.sourceLedgerCode()).isEqualTo(inbound.code());
            assertThat(reversal.to()).isNull();
            assertThat(reversal.documentType()).isEqualTo("inbound_reversal");
            assertThat(repository.findBalance(raw, session).quantity()).isEqualByComparingTo("0");
            assertThatThrownBy(() -> service.reverseInbound(new ReceiptEntryReversal(
                    inbound.code(), "MD-1", raw, new BigDecimal("2"), new BigDecimal("5"), "重复冲正"), session))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不足");
            connection.rollback();
        }
        assertThat(repository.findBalance(raw).quantity()).isEqualByComparingTo("2");
        assertThat(service.ledgers()).extracting(StockLedgerEntry::documentType).containsExactly("inbound_confirm");
        assertThat(service.balances()).noneMatch(balance -> balance.dimension().stockStatus() == StockStatus.SCRAP
                || balance.dimension().stockStatus() == StockStatus.FROZEN);
    }

    @Test
    void 隔离MySQL冲正失败后原余额和流水一起回滚() throws Exception {
        MysqlDataSource dataSource = mysql();
        JdbcStockLedgerRepository repository = new JdbcStockLedgerRepository(dataSource);
        StockLedgerService service = new StockLedgerService(repository);
        StockDimension raw = new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-REV-" + System.nanoTime(),
                null, "AU", "RAW-CUSTOMER", StockStatus.RAW_WAITING);
        StockLedgerEntry inbound = service.transfer(new StockTransferCommand(
                "inbound_confirm", raw.motherOrderCode(), null, raw, new BigDecimal("2"), new BigDecimal("5"), "收货"));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            WorkSession session = new WorkSession() {
                @Override
                public Optional<Connection> connection() {
                    return Optional.of(connection);
                }

                @Override
                public void onRollback(Runnable undo) {
                }
            };
            service.reverseInbound(new ReceiptEntryReversal(
                    inbound.code(), raw.motherOrderCode(), raw, new BigDecimal("2"), new BigDecimal("5"), "录错冲正"), session);
            assertThatThrownBy(() -> service.reverseInbound(new ReceiptEntryReversal(
                    inbound.code(), raw.motherOrderCode(), raw, new BigDecimal("2"), new BigDecimal("5"), "重复冲正"), session))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不足");
            connection.rollback();
        }
        assertThat(repository.findBalance(raw).quantity()).isEqualByComparingTo("2");
        assertThat(service.ledgers().stream().filter(entry -> raw.motherOrderCode().equals(entry.documentCode()))
                .map(StockLedgerEntry::documentType)).containsExactly("inbound_confirm");
    }

    private MysqlDataSource mysql() throws SQLException {
        String url = System.getenv("A3_MYSQL_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "未指定独立 MySQL 校验库");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/a3_verify_[A-Za-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("冲正测试只允许连接本机 a3_verify_ 独立库");
        }
        MysqlDataSource source = new MysqlDataSource();
        source.setURL(url);
        source.setUser(System.getenv("A3_MYSQL_DB_USER"));
        source.setPassword(System.getenv("A3_MYSQL_DB_PASSWORD"));
        return source;
    }

    private CommandCall call(String requestId) {
        return new CommandCall(requestId, "管理员", "REVERSE_INBOUND", "MOTHER", "MD-1", "payload");
    }

    private StockDimension raw() {
        return new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-1", null, "AU", "RAW-CUSTOMER",
                StockStatus.RAW_WAITING);
    }
}
