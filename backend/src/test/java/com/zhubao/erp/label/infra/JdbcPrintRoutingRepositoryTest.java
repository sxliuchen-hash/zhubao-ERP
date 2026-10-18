package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTargetJob;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcPrintRoutingRepositoryTest {
    @Test
    void 批次任务同事务且领取限定目标工位() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:print_route_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        String sql = Files.readString(Path.of("src/main/resources/db/mysql/V61__m3_print_routing_batch.sql"), StandardCharsets.UTF_8);
        try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
            s.execute("create table biz_print_job (status_code varchar(20), failure_reason varchar(255), content_snapshot varchar(1000))");
            s.execute("insert into biz_print_job values ('PENDING', '', 'old snapshot')");
            for (String part : sql.split(";")) if (!part.isBlank()) s.execute(part);
            s.execute("alter table biz_print_target_job add column recycled_at varchar(40)");
            s.execute("alter table biz_print_target_job add column recycled_by varchar(80)");
            s.execute("alter table biz_print_target_job add column recycle_batch varchar(40)");
            s.execute("alter table biz_print_target_job add column recycle_reason varchar(200)");
        }
        try (Connection c = source.getConnection(); Statement s = c.createStatement();
             var r = s.executeQuery("select status_code, content_snapshot from biz_print_job")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getString(1)).isEqualTo("UNCERTAIN");
            assertThat(r.getString(2)).isEqualTo("old snapshot");
        }
        JdbcPrintRoutingRepository repository = new JdbcPrintRoutingRepository(source);
        repository.saveStation(new PrintStation("A", "收货", true, "", "hash-a"));
        repository.saveStation(new PrintStation("B", "包装", true, "", "hash-b"));
        repository.saveQueue(new PrintQueue("A", "Q1", "主队列", "ALL", 40, 30, true, true));
        PrintBatch batch = batch("PB-1", "req-1", List.of(job("J-1", "PB-1", "A", "Q1"), job("J-2", "PB-1", "B", "Q1")));
        repository.insertBatch(batch);
        assertThat(repository.batchByRequest("req-1").orElseThrow().jobs()).hasSize(2);
        assertThat(repository.claim("B", "tomorrow", "now").orElseThrow().id()).isEqualTo("J-2");
        assertThat(repository.claim("B", "tomorrow", "now")).isEmpty();
        assertThat(repository.claim("A", "tomorrow", "now").orElseThrow().id()).isEqualTo("J-1");
        PrintBatch bad = batch("PB-2", "req-2", List.of(job("J-3", "PB-2", "A", "Q1"),
                job("J-4", "PB-2", "A", "Q1")));
        assertThatThrownBy(() -> repository.insertBatch(bad)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.batchByRequest("req-2")).isEmpty();
        repository.insertBatch(batch("PB-3", "req-3", List.of(job("J-5", "PB-3", "A", "Q1"))));
        PrintBatch moved = batch("PB-4", "req-4", List.of(job("J-6", "PB-4", "B", "Q1")));
        assertThat(repository.rerouteJob("J-5", PrintJobStatus.PENDING, moved, "原工位停机", "later")).isPresent();
        assertThat(repository.job("J-5").orElseThrow().status()).isEqualTo(PrintJobStatus.CANCELED);
        assertThat(repository.batchByRequest("req-4").orElseThrow().jobs()).hasSize(1);
        assertThat(repository.rerouteJob("J-5", PrintJobStatus.PENDING,
                batch("PB-5", "req-5", List.of(job("J-7", "PB-5", "A", "Q1"))), "再次改派", "later"))
                .isEmpty();
        repository.insertBatch(batch("PB-6", "req-6", List.of(job("J-8", "PB-6", "A", "Q1"))));
        PrintBatch collision = batch("PB-7", "req-7", List.of(job("J-6", "PB-7", "B", "Q1")));
        assertThatThrownBy(() -> repository.rerouteJob("J-8", PrintJobStatus.PENDING, collision,
                "测试回滚", "later")).isInstanceOf(IllegalStateException.class);
        assertThat(repository.job("J-8").orElseThrow().status()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(repository.batchByRequest("req-7")).isEmpty();
    }
    private PrintBatch batch(String id, String requestId, List<PrintTargetJob> jobs) {
        return new PrintBatch(id, requestId, "MD-1", "FLOW", 1, "hash", "{}", "operator", "now", jobs);
    }
    private PrintTargetJob job(String id, String batch, String station, String queue) {
        return new PrintTargetJob(id, batch, station, queue, 1, PrintJobStatus.PENDING, "", "", "", "", "", "", "now", "now");
    }
}
