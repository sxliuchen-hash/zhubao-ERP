package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.business.infra.JdbcPortionInspectionRepository;
import com.zhubao.erp.business.infra.JdbcPortionReleaseRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
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

class PortionLimitConcurrencyTest {

    @Test
    void 并发检查和冻结都不能超过本袋数量() throws Exception {
        JdbcDataSource dataSource = database();
        JdbcActivityBagRepository bags = new JdbcActivityBagRepository(dataSource);
        JdbcPortionInspectionRepository inspections = new JdbcPortionInspectionRepository(dataSource);
        JdbcPortionReleaseRepository releases = new JdbcPortionReleaseRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        PermissionGuard guard = new PermissionGuard();
        OperatorContext inspector = new OperatorContext("质检", RoleCode.QUALITY_INSPECTOR, DataScope.ALL);
        String bagId = unit.execute(new CommandCall("rf10-open01", "质检", "BAG_OPEN", "BAG", "MD-20261030-1010", "open"),
                session -> {
                    ActivityBag bag = new ActivityBag("BAG-RF10", "MD-20261030-1010", "牙骨", "REQ-1", "LINE-1", "",
                            false, new BigDecimal("10"), StockMeasurement.unknown(), 1);
                    bags.save(bag, 0, session);
                    return bag.id();
                });
        PortionInspectionService inspection = new PortionInspectionService(bags, inspections, guard, unit);
        List<String> inspected = race(List.of("rf10-insp-a1", "rf10-insp-b1"), requestId -> {
            try {
                inspection.record(requestId, bagId, 1, "REQ-1", new BigDecimal("8"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), inspector);
                return "OK";
            } catch (BusinessException ex) {
                return ex.getMessage();
            }
        });
        assertThat(inspected).containsExactlyInAnyOrder("OK", "检查数量超过本袋份额");
        assertThat(inspections.listByBag(bagId)).hasSize(1);

        inspection.record("rf10-insp-c1", bagId, 1, "REQ-1", new BigDecimal("2"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), inspector);
        PortionReleaseService release = new PortionReleaseService(
                bags, inspections, releases, new com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository(),
                new com.zhubao.erp.business.infra.InMemoryNodePortionReader(),
                new WeighingService(bags, new com.zhubao.erp.business.infra.InMemoryBagWeightRepository(), unit),
                guard, unit);
        List<String> frozen = race(List.of("rf10-frz-a01", "rf10-frz-b01"), requestId -> {
            try {
                release.freeze(requestId, bagId, 1, new BigDecimal("8"), inspector);
                return "OK";
            } catch (BusinessException ex) {
                return ex.getMessage();
            }
        });
        assertThat(frozen).containsExactlyInAnyOrder("OK", "冻结数量不能超过本袋份额");
        assertThat(releases.find(bagId).orElseThrow().frozen()).isEqualByComparingTo("8");
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
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : List.of(
                    "V44__m3_command_request.sql", "V81__m3_activity_bag.sql",
                    "V85__m3_portion_inspection.sql", "V86__m3_portion_release.sql")) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql", file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }
}
