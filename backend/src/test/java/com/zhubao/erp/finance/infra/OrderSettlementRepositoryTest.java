package com.zhubao.erp.finance.infra;

import com.zhubao.erp.business.domain.OrderDeliveryAssessment;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OrderSettlement;
import com.zhubao.erp.finance.domain.OrderSettlementItem;
import com.zhubao.erp.finance.domain.OrderSettlementRepository;
import com.zhubao.erp.finance.domain.SettlementItemKind;
import com.zhubao.erp.finance.domain.SettlementStatus;
import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.domain.WorkSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderSettlementRepositoryTest {

    @Test
    void memoryDraftKeepsVersionAndRollsBack() {
        InMemoryOrderSettlementRepository repository = new InMemoryOrderSettlementRepository();
        MemorySession session = new MemorySession();
        OrderSettlement created = repository.saveDraft(draft("SET-1"), List.of(item()), List.of(), 0, session);
        assertThat(created.rowVersion()).isEqualTo(1);
        assertThat(created.status()).isEqualTo(SettlementStatus.DRAFT);
        assertThatThrownBy(() -> repository.saveDraft(draft("SET-2"), List.of(), List.of(), 0, session))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有结单草稿");
        OrderSettlement updated = repository.saveDraft(draft("SET-1"), List.of(item()), List.of(), 1, session);
        assertThat(updated.rowVersion()).isEqualTo(2);
        assertThatThrownBy(() -> repository.saveDraft(draft("SET-1"), List.of(), List.of(), 1, session))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已变化");
        session.rollback();
        assertThat(repository.find("SET-1", null)).isEmpty();
    }

    @Test
    void memoryConfirmRejectsSecondOpening() {
        InMemoryOrderSettlementRepository repository = new InMemoryOrderSettlementRepository();
        MemorySession session = new MemorySession();
        repository.saveDraft(draft("SET-1"), List.of(item()), List.of(), 0, session);
        OrderSettlement confirmed = repository.markConfirmed("SET-1", 1, "财务", "2026-10", "2026-10-27T10:00:00", session);
        assertThat(confirmed.status()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThatThrownBy(() -> repository.markConfirmed("SET-1", 2, "财务", "2026-10", "2026-10-27T10:00:00", session))
                .isInstanceOf(BusinessException.class);
        assertThat(repository.policyEnabled()).isTrue();
    }

    @Test
    void jdbcDraftAndConfirmUseTheSameConnection() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        List<MigrationScript> migrations = MigrationCatalog.loadClasspath();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (int index : new int[]{35, 77}) {
                for (String sql : MigrationCatalog.statements(migrations.get(index).sql())) {
                    if (sql.contains("sys_navigation_menu") || sql.contains("sys_role_menu")) {
                        continue;
                    }
                    statement.execute(sql);
                }
            }
        }
        JdbcOrderSettlementRepository repository = new JdbcOrderSettlementRepository(dataSource);
        assertThat(repository.policyEnabled()).isFalse();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            WorkSession session = new ConnectionSession(connection);
            repository.saveDraft(draft("SET-1"), List.of(item()), List.of(), 0, session);
            connection.rollback();
        }
        assertThat(repository.find("SET-1", null)).isEmpty();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            WorkSession session = new ConnectionSession(connection);
            OrderSettlement created = repository.saveDraft(draft("SET-1"), List.of(item()), List.of(), 0, session);
            connection.commit();
            assertThat(created.status()).isEqualTo(SettlementStatus.DRAFT);
        }
        Optional<OrderSettlement> stored = repository.find("SET-1", null);
        assertThat(stored).isPresent();
        assertThat(repository.items("SET-1", null)).hasSize(1);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            WorkSession session = new ConnectionSession(connection);
            repository.markConfirmed("SET-1", stored.orElseThrow().rowVersion(), "财务", "2026-10", "2026-10-27T10:00:00", session);
            connection.commit();
        }
        assertThat(repository.find("SET-1", null).orElseThrow().status()).isEqualTo(SettlementStatus.CONFIRMED);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            WorkSession session = new ConnectionSession(connection);
            assertThatThrownBy(() -> repository.saveDraft(draft("SET-9"), List.of(), List.of(), 0, session))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已确认");
            connection.rollback();
        }
    }

    private static OrderSettlement draft(String code) {
        return new OrderSettlement(code, code, 1, "", OrderDeliveryAssessment.MOTHER, "MD-1", "MD-1", 101L, "真实客户",
                SettlementStatus.DRAFT, 0, "rev", new BigDecimal("10.00"), new BigDecimal("8.00"), false, "",
                false, "", "制单", "", "2026-10-27T09:00:00", "", "", "NONE");
    }

    private static OrderSettlementItem item() {
        return new OrderSettlementItem(1, SettlementItemKind.CUSTOMER, "加工费", "", "FIXED", null, "件", "",
                null, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"), "", "", "", "");
    }

    private static final class MemorySession implements WorkSession {
        private final List<Runnable> undos = new ArrayList<>();

        @Override
        public Optional<Connection> connection() {
            return Optional.empty();
        }

        @Override
        public void onRollback(Runnable undo) {
            undos.add(undo);
        }

        private void rollback() {
            for (int index = undos.size() - 1; index >= 0; index--) {
                undos.get(index).run();
            }
        }
    }

    private static final class ConnectionSession implements WorkSession {
        private final Connection jdbc;

        private ConnectionSession(Connection jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Connection> connection() {
            return Optional.of(jdbc);
        }

        @Override
        public void onRollback(Runnable undo) {
        }
    }
}
