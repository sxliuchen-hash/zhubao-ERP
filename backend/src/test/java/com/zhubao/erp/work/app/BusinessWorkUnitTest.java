package com.zhubao.erp.work.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 请求回放和工作单元。同内容重试不重复写账；单据或结果保存失败时整单回滚。
 * 这里用探针表代替尚未接入的库存和出货单。
 */
class BusinessWorkUnitTest {

    @Test
    void memoryReplaysSamePayloadAndRollsBackFailedWork() {
        assertUnit(new MemoryHarness());
    }

    @Test
    void jdbcReplaysSamePayloadAndRollsBackFailedWork() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            String sql = Files.readString(Path.of("src/main/resources/db/mysql/V44__m3_command_request.sql"), StandardCharsets.UTF_8);
            for (String item : MigrationCatalog.statements(sql)) {
                statement.execute(item);
            }
            statement.execute("create table probe_unit_ledger (code varchar(40) primary key, note varchar(80) not null)");
            statement.execute("create table probe_unit_shipment (code varchar(40) primary key, ledger_code varchar(40) not null)");
        }
        assertUnit(new JdbcHarness(dataSource));
    }

    private void assertUnit(Harness harness) {
        AtomicInteger runs = new AtomicInteger();
        BusinessWorkUnit unit = harness.unit(CommandResultProbe.NONE);
        CommandCall call = call("req-unit-0001", "wh-s5", "SHIP", "WORK_ORDER", "ZD-1", "数量 1");

        String first = unit.execute(call, session -> {
            runs.incrementAndGet();
            harness.addLedger(session, "STK-1");
            harness.addShipment(session, "OUT-1", "STK-1");
            return "STK-1|OUT-1";
        });
        String retried = unit.execute(new CommandCall(
                "req-unit-0001", "wh-s5", "SHIP", "WORK_ORDER", "ZD-1", "  数量   1  "
        ), session -> {
            runs.incrementAndGet();
            harness.addLedger(session, "STK-2");
            harness.addShipment(session, "OUT-2", "STK-2");
            return "再次扣减";
        });

        assertThat(first).isEqualTo("STK-1|OUT-1");
        assertThat(retried).isEqualTo(first);
        assertThat(runs).hasValue(1);
        assertThat(harness.ledgers()).isEqualTo(1);
        assertThat(harness.shipments()).isEqualTo(1);
        assertThat(harness.requests()).isEqualTo(1);

        assertThatThrownBy(() -> unit.execute(call("req-unit-0001", "wh-s5", "SHIP", "WORK_ORDER", "ZD-2", "数量 1"), session -> {
            runs.incrementAndGet();
            return "换了目标";
        })).isInstanceOf(BusinessException.class).hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> unit.execute(call("req-unit-0001", "other-user", "SHIP", "WORK_ORDER", "ZD-1", "数量 1"), session -> {
            runs.incrementAndGet();
            return "别人的请求";
        })).isInstanceOf(PermissionDeniedException.class).hasMessage("该请求不能跨账号重放");
        assertThat(runs).hasValue(1);
        assertThat(harness.ledgers()).isEqualTo(1);

        assertThatThrownBy(() -> unit.execute(call("req-unit-0002", "wh-s5", "SHIP", "WORK_ORDER", "ZD-9", "数量 1"), session -> {
            runs.incrementAndGet();
            harness.addLedger(session, "STK-FAIL");
            throw new BusinessException("出货单保存失败");
        })).isInstanceOf(BusinessException.class).hasMessage("出货单保存失败");
        assertThat(harness.ledgers()).isEqualTo(1);
        assertThat(harness.shipments()).isEqualTo(1);
        assertThat(harness.requests()).isEqualTo(1);

        BusinessWorkUnit failingResult = harness.unit(() -> {
            throw new IllegalStateException("请求结果保存失败");
        });
        assertThatThrownBy(() -> failingResult.execute(call("req-unit-0003", "wh-s5", "SHIP", "WORK_ORDER", "ZD-8", "数量 1"), session -> {
            runs.incrementAndGet();
            harness.addLedger(session, "STK-RESULT");
            harness.addShipment(session, "OUT-RESULT", "STK-RESULT");
            return "STK-RESULT|OUT-RESULT";
        })).isInstanceOf(IllegalStateException.class).hasMessage("请求结果保存失败");
        assertThat(harness.ledgers()).isEqualTo(1);
        assertThat(harness.shipments()).isEqualTo(1);
        assertThat(harness.requests()).isEqualTo(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<String> left = pool.submit(() -> {
                start.await();
                return unit.execute(call("req-unit-0004", "wh-s5", "SHIP", "WORK_ORDER", "ZD-4", "并发"), session -> {
                    runs.incrementAndGet();
                    harness.addLedger(session, "STK-RACE");
                    harness.addShipment(session, "OUT-RACE", "STK-RACE");
                    return "STK-RACE|OUT-RACE";
                });
            });
            Future<String> right = pool.submit(() -> {
                start.await();
                return unit.execute(call("req-unit-0004", "wh-s5", "SHIP", "WORK_ORDER", "ZD-4", "并发"), session -> {
                    runs.incrementAndGet();
                    harness.addLedger(session, "STK-RACE-2");
                    harness.addShipment(session, "OUT-RACE-2", "STK-RACE-2");
                    return "第二次扣减";
                });
            });
            start.countDown();
            assertThat(left.get()).isEqualTo("STK-RACE|OUT-RACE");
            assertThat(right.get()).isEqualTo("STK-RACE|OUT-RACE");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
        assertThat(harness.ledgers()).isEqualTo(2);
        assertThat(harness.shipments()).isEqualTo(2);
        assertThat(harness.requests()).isEqualTo(2);
    }

    private CommandCall call(String requestId, String actorId, String action, String targetType, String targetCode, String payload) {
        return new CommandCall(requestId, actorId, action, targetType, targetCode, payload);
    }

    private interface Harness {
        BusinessWorkUnit unit(CommandResultProbe probe);

        void addLedger(WorkSession session, String code);

        void addShipment(WorkSession session, String code, String ledgerCode);

        int ledgers();

        int shipments();

        int requests();
    }

    private static final class MemoryHarness implements Harness {
        private final InMemoryCommandRequestRepository requests = new InMemoryCommandRequestRepository();
        private final List<String> ledgers = new ArrayList<>();
        private final List<String> shipments = new ArrayList<>();

        @Override
        public BusinessWorkUnit unit(CommandResultProbe probe) {
            return new MemoryBusinessWorkUnit(requests, probe);
        }

        @Override
        public void addLedger(WorkSession session, String code) {
            ledgers.add(code);
            session.onRollback(() -> ledgers.remove(code));
        }

        @Override
        public void addShipment(WorkSession session, String code, String ledgerCode) {
            String row = code + "|" + ledgerCode;
            shipments.add(row);
            session.onRollback(() -> shipments.remove(row));
        }

        @Override
        public int ledgers() {
            return ledgers.size();
        }

        @Override
        public int shipments() {
            return shipments.size();
        }

        @Override
        public int requests() {
            return List.of("req-unit-0001", "req-unit-0002", "req-unit-0003", "req-unit-0004").stream()
                    .filter(id -> requests.findByRequestId(id).isPresent())
                    .toList()
                    .size();
        }
    }

    private static final class JdbcHarness implements Harness {
        private final DataSource dataSource;
        private final JdbcCommandRequestRepository requests;

        private JdbcHarness(DataSource dataSource) {
            this.dataSource = dataSource;
            this.requests = new JdbcCommandRequestRepository(dataSource);
        }

        @Override
        public BusinessWorkUnit unit(CommandResultProbe probe) {
            return new JdbcBusinessWorkUnit(dataSource, requests, probe);
        }

        @Override
        public void addLedger(WorkSession session, String code) {
            update(session, "insert into probe_unit_ledger(code, note) values (?, ?)", code, "扣减");
        }

        @Override
        public void addShipment(WorkSession session, String code, String ledgerCode) {
            update(session, "insert into probe_unit_shipment(code, ledger_code) values (?, ?)", code, ledgerCode);
        }

        @Override
        public int ledgers() {
            return count("select count(*) from probe_unit_ledger");
        }

        @Override
        public int shipments() {
            return count("select count(*) from probe_unit_shipment");
        }

        @Override
        public int requests() {
            return count("select count(*) from biz_command_request where result_text <> ''");
        }

        private void update(WorkSession session, String sql, String first, String second) {
            Connection connection = session.connection().orElseThrow();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, first);
                statement.setString(2, second);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private int count(String sql) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                rs.next();
                return rs.getInt(1);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
