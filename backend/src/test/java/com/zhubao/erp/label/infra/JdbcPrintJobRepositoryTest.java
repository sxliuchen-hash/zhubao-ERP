package com.zhubao.erp.label.infra;

import com.zhubao.erp.label.app.PrintJobService;
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
        PrintJobService service = new PrintJobService(new JdbcPrintJobRepository(dataSource), "station-secret");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PrintJob> first = pool.submit(() -> {
                start.await();
                return service.create("jdbc-print-race", "MD-JDBC-PRINT", "FLOW", 1, "何琳");
            });
            Future<PrintJob> second = pool.submit(() -> {
                start.await();
                return service.create("jdbc-print-race", "MD-JDBC-PRINT", "FLOW", 1, "何琳");
            });
            start.countDown();
            assertThat(first.get().id()).isEqualTo(second.get().id());
        } finally {
            pool.shutdownNow();
        }
        PrintJob leased = service.claim("STATION-1", "station-secret");
        PrintJob replay = service.create("jdbc-print-race", "MD-JDBC-PRINT", "FLOW", 1, "何琳");
        assertThat(replay.id()).isEqualTo(leased.id());
        assertThat(replay.status()).isEqualTo(PrintJobStatus.LEASED);
        PrintJob reprint = service.retry(leased.id(), "jdbc-print-retry", "同版补打", "何琳");
        assertThat(reprint.contentSnapshot()).isEqualTo(leased.contentSnapshot());
        assertThat(reprint.status()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(service.get(leased.id()).status()).isEqualTo(PrintJobStatus.LEASED);
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:print-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}