package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ProcessBatchHandoff;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

/** 分批仓储必须在同一连接内串行累计、回放及回滚。 */
class JdbcProcessBatchTest {
    private JdbcDataSource database() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            for (String file : new String[]{"V44__m3_command_request.sql", "V73__m3_process_batch_handoff.sql"})
                for (String sql : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql/" + file)))) statement.execute(sql);
        }
        return source;
    }
    private CommandCall call(String id) { return new CommandCall(id, "工人甲", "PROCESS_BATCH_HANDOFF", "WORK_ORDER", "ZD-1", "quantity=3"); }
    @Test void differentRequestsSerializeAndReplayWithoutOverDelivery() throws Exception {
        var source = database(); var repository = new JdbcBusinessDocumentRepository(source);
        var unit = new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source));
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> submit = () -> {
                try {
                    String id = UUID.randomUUID().toString();
                    unit.execute(call(id), session -> {
                        repository.lockProcessBatchOrder("ZD-1", session);
                        BigDecimal total = repository.listProcessBatches("ZD-1", session).stream().map(ProcessBatchHandoff::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (total.add(new BigDecimal("3")).compareTo(new BigDecimal("4")) > 0) throw new BusinessException("超过可交数量");
                        repository.saveProcessBatch(new ProcessBatchHandoff(id, "ZD-1", "雕刻", new BigDecimal("3"), null, "工人甲", "2026-10-24T09:00:00"), session);
                        return id;
                    }); return true;
                } catch (BusinessException ex) { return false; }
            };
            var first = pool.submit(submit); var second = pool.submit(submit);
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
            var saved = repository.listProcessBatches("ZD-1", null); assertThat(saved).hasSize(1);
            String id = saved.get(0).batchCode();
            assertThat(unit.execute(call(id), session -> { throw new AssertionError("不应重复执行"); })).isEqualTo(id);
            assertThat(new JdbcBusinessDocumentRepository(source).listAllProcessBatches()).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void failedResultRollsBackBatchAndFinalWeight() throws Exception {
        var source = database(); var repository = new JdbcBusinessDocumentRepository(source);
        var unit = new JdbcBusinessWorkUnit(source, new JdbcCommandRequestRepository(source), () -> { throw new IllegalStateException("回执失败"); });
        assertThatThrownBy(() -> unit.execute(call("batch-fail"), session -> {
            repository.lockProcessBatchOrder("ZD-1", session);
            repository.saveProcessBatch(new ProcessBatchHandoff("PB-FAIL", "ZD-1", "抛光", BigDecimal.ZERO, new BigDecimal("7.8"), "工人甲", "2026-10-24T09:00:00"), session);
            assertThat(repository.listProcessBatches("ZD-1", session)).hasSize(1);
            return "已登记";
        })).hasMessageContaining("回执失败");
        assertThat(repository.listAllProcessBatches()).isEmpty();
    }
}
