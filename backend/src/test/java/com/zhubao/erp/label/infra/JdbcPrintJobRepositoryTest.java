package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPrintJobRepositoryTest {

    @Test
    void concurrentRequestReturnsTheStoredJobAndReplayDoesNotResetLease() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table biz_print_job (
                        id varchar(40) primary key,
                        request_id varchar(64) not null,
                        business_code varchar(90) not null,
                        template_code varchar(64) not null,
                        copies int not null,
                        content_snapshot varchar(4000) not null,
                        status_code varchar(20) not null,
                        station_id varchar(64) not null,
                        lease_until varchar(40) not null,
                        failure_reason varchar(200) not null,
                        predecessor_id varchar(40) not null,
                        retry_reason varchar(200) not null,
                        created_by varchar(64) not null,
                        created_at varchar(40) not null,
                        updated_at varchar(40) not null,
                        recycled_at varchar(40),
                        recycled_by varchar(80),
                        recycle_batch varchar(40),
                        recycle_reason varchar(200),
                        unique key uk_print_job_request (request_id)
                    )
                    """);
        }
        JdbcPrintJobRepository repository = new JdbcPrintJobRepository(dataSource);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PrintJob> first = pool.submit(() -> {
                start.await();
                return repository.insertIfAbsent(history());
            });
            Future<PrintJob> second = pool.submit(() -> {
                start.await();
                return repository.insertIfAbsent(history());
            });
            start.countDown();
            assertThat(first.get().id()).isEqualTo(second.get().id());
        } finally {
            pool.shutdownNow();
        }
        PrintJob original = repository.list().get(0);
        PrintJob leased = repository.save(new PrintJob(original.id(), original.requestId(), original.businessCode(), original.templateCode(), original.copies(),
                original.contentSnapshot(), PrintJobStatus.LEASED, "STATION-1", "2099-01-01", "", "", "", "何琳", "2026-10-28", "2026-10-28"));
        PrintJob replay = repository.insertIfAbsent(history());
        assertThat(replay.id()).isEqualTo(leased.id());
        assertThat(replay.status()).isEqualTo(PrintJobStatus.LEASED);
        assertThat(repository.findById(leased.id()).orElseThrow().contentSnapshot()).isEqualTo(original.contentSnapshot());
    }

    private static PrintJob history() {
        return new PrintJob("PJ-" + UUID.randomUUID().toString().substring(0, 8), "jdbc-print-race", "MD-JDBC-PRINT", "FLOW", 1, "{}", PrintJobStatus.SPOOLED,
                "STATION-1", "", "", "", "", "何琳", "2026-10-28", "2026-10-28");
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:print-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}