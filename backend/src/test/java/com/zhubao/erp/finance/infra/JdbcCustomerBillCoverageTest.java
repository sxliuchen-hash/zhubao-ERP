package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.CustomerBillCoverage;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCustomerBillCoverageTest {

    @Test
    void 并发覆盖同一旧凭据不能超过原额() throws Exception {
        JdbcDataSource dataSource = database();
        JdbcCustomerDeliveryBillRepository bills = new JdbcCustomerDeliveryBillRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        List<String> results = race(List.of("rf16-cov-a1", "rf16-cov-b1"), requestId -> {
            try {
                String billId = "CB" + (requestId.endsWith("a1") ? "a" : "b").repeat(32);
                unit.execute(new CommandCall(requestId, "账务员", "COVER_LEGACY", "RECEIVABLE", "YS-RF16", requestId),
                        session -> {
                            bills.saveCoverage(new CustomerBillCoverage(billId, "YS-RF16", new BigDecimal("80.00")),
                                    new BigDecimal("100.00"), session);
                            return billId;
                        });
                return "OK";
            } catch (BusinessException ex) {
                return ex.getMessage();
            }
        });
        assertThat(results).containsExactlyInAnyOrder("OK", "覆盖金额超过旧凭据剩余额度");
        assertThat(bills.coveredTotal("YS-RF16")).isEqualByComparingTo("80.00");
    }

    private static List<String> race(List<String> requestIds, java.util.function.Function<String, String> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = requestIds.stream().map(id -> pool.submit(() -> action.apply(id))).toList();
            return List.of(futures.get(0).get(), futures.get(1).get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static JdbcDataSource database() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:coverage-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : List.of("V44__m3_command_request.sql", "V134__m3_customer_bill_coverage.sql")) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql", file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }
}
