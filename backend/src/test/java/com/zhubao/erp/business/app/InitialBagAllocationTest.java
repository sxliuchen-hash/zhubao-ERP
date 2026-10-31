package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitialBagAllocationTest {

    @Test
    void 多来源各建一袋且重复安排不追加() {
        InitialBagAllocationService service = service(new InMemoryActivityBagRepository());
        List<InitialBagAllocationService.SourceLine> lines = List.of(
                line("LINE-1", "10"),
                line("LINE-2", "6"));
        List<ActivityBag> first = service.arrange("ZD-20261030-0008", lines, null);
        List<ActivityBag> second = service.arrange("ZD-20261030-0008", lines, null);

        assertThat(first).hasSize(2);
        assertThat(first).extracting(ActivityBag::sourceKey).containsExactly("LINE-1", "LINE-2");
        assertThat(first).extracting(ActivityBag::id).containsExactlyElementsOf(second.stream().map(ActivityBag::id).toList());
        assertThat(first).allMatch(bag -> !bag.closed());
    }

    @Test
    void 同一来源不能超过真实数量() {
        InMemoryActivityBagRepository repository = new InMemoryActivityBagRepository();
        InitialBagAllocationService service = service(repository);
        List<ActivityBag> first = service.arrange("ZD-A", List.of(line("LINE-1", "6")), null);
        List<ActivityBag> second = service.arrange("ZD-B", List.of(line("LINE-1", "4")), null);
        assertThat(second).hasSize(1);
        assertThatThrownBy(() -> service.arrange("ZD-C", List.of(line("LINE-1", "1")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("来源剩余不足");
        List<ActivityBag> replay = service.arrange("ZD-A", List.of(line("LINE-1", "6")), null);
        assertThat(replay).extracting(ActivityBag::id).containsExactly(first.get(0).id());
        assertThat(repository.listBySource("LINE-1").stream().filter(ActivityBag::activeLeaf)
                .map(ActivityBag::quantity).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10");
        assertThat(second.get(0).quantity()).isEqualByComparingTo("4");
    }

    @Test
    void 并发分配同一来源不会超量() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : List.of(
                    "V44__m3_command_request.sql", "V81__m3_activity_bag.sql", "V131__m3_source_allocation_guard.sql")) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql", file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        JdbcActivityBagRepository repository = new JdbcActivityBagRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        InitialBagAllocationService service = new InitialBagAllocationService(
                new ActivityBagService(repository), key -> "LINE-1".equals(key) ? new BigDecimal("10") : null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = List.of("ZD-1", "ZD-2").stream().map(target -> pool.submit(() -> {
                try {
                    return unit.execute(new CommandCall(
                            "rf04-" + target, "wh", "BAG_ALLOCATE", "BAG", target, "10"), session -> {
                        service.arrange(target, List.of(line("LINE-1", "10")), session);
                        return "OK";
                    });
                } catch (BusinessException ex) {
                    return ex.getMessage();
                }
            })).toList();
            List<String> results = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(results).containsExactlyInAnyOrder("OK", "来源剩余不足");
        } finally {
            pool.shutdownNow();
        }
        assertThat(repository.listBySource("LINE-1").stream().map(ActivityBag::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10");
    }

    private static InitialBagAllocationService service(InMemoryActivityBagRepository repository) {
        return new InitialBagAllocationService(new ActivityBagService(repository), key -> switch (key) {
            case "LINE-1" -> new BigDecimal("10");
            case "LINE-2" -> new BigDecimal("6");
            default -> null;
        });
    }

    private static InitialBagAllocationService.SourceLine line(String key, String quantity) {
        return new InitialBagAllocationService.SourceLine(
                key, "AU9999", "REQ-1", new BigDecimal(quantity), StockMeasurement.unknown());
    }
}
