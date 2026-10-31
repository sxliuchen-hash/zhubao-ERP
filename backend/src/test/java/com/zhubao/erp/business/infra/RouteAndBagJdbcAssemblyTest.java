package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.app.RouteSnapshotService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteAndBagJdbcAssemblyTest {

    @Test
    void mysql档案使用袋和路线的jdbc实现且内存仍是默认() throws Exception {
        String mysql = Files.readString(Path.of("src/main/resources/application-mysql.yml"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        String local = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(mysql).contains("activity-bag:\n    repository: ${ZHUBAO_ACTIVITY_BAG_REPOSITORY:jdbc}");
        assertThat(mysql).contains("route-snapshot:\n    repository: ${ZHUBAO_ROUTE_SNAPSHOT_REPOSITORY:jdbc}");
        assertThat(local).doesNotContain("activity-bag:").doesNotContain("route-snapshot:");

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:assembly;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        ApplicationContextRunner jdbc = runner(dataSource)
                .withPropertyValues("zhubao.activity-bag.repository=jdbc", "zhubao.route-snapshot.repository=jdbc");
        jdbc.run(context -> {
            assertThat(context).hasSingleBean(JdbcActivityBagRepository.class);
            assertThat(context).hasSingleBean(JdbcRouteSnapshotRepository.class);
            assertThat(context).doesNotHaveBean(InMemoryActivityBagRepository.class);
            assertThat(context).doesNotHaveBean(InMemoryRouteSnapshotRepository.class);
            assertThat(context.getBean(ActivityBagRepository.class)).isInstanceOf(JdbcActivityBagRepository.class);
            assertThat(context.getBean(RouteSnapshotRepository.class)).isInstanceOf(JdbcRouteSnapshotRepository.class);
        });
        runner(dataSource).run(context -> {
            assertThat(context).hasSingleBean(InMemoryActivityBagRepository.class);
            assertThat(context).hasSingleBean(InMemoryRouteSnapshotRepository.class);
            assertThat(context).doesNotHaveBean(JdbcActivityBagRepository.class);
            assertThat(context).doesNotHaveBean(JdbcRouteSnapshotRepository.class);
        });
    }

    @Test
    void 路线和袋落库后新仓储仍能读到且失败一起回滚() throws Exception {
        JdbcDataSource dataSource = database();
        JdbcRouteSnapshotRepository routes = new JdbcRouteSnapshotRepository(dataSource);
        JdbcActivityBagRepository bags = new JdbcActivityBagRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        RouteSnapshotService service = new RouteSnapshotService(routes);
        ActivityBagService bagService = new ActivityBagService(bags);
        String target = "MD-20261030-0202";

        String snapshotId = unit.execute(new CommandCall(
                "rf02-save", "库管", "ROUTE_BAG_SAVE", "MOTHER_ORDER", target, "save"), session -> {
            RouteSnapshot captured = service.capture(BusinessKind.PROCESSING, target, "牙骨", session);
            bagService.open(target, "牙骨", "REQ-1", "LINE-1", new BigDecimal("10"), StockMeasurement.unknown(), session);
            return captured.snapshotId();
        });
        unit.execute(new CommandCall(
                "rf02-confirm", "库管", "ROUTE_CONFIRM", "MOTHER_ORDER", target, snapshotId), session -> {
            service.confirm(snapshotId, session);
            return "CONFIRMED";
        });

        RouteSnapshot restored = new JdbcRouteSnapshotRepository(dataSource).find(snapshotId).orElseThrow();
        assertThat(restored.confirmed()).isTrue();
        assertThat(restored.stepIds()).hasSize(15).contains("POLISH_BEAD").doesNotContain("POLISH_GOLD");
        List<ActivityBag> leaves = new JdbcActivityBagRepository(dataSource).listByTarget(target);
        assertThat(leaves).hasSize(1);
        assertThat(leaves.get(0).quantity()).isEqualByComparingTo("10");

        assertThatThrownBy(() -> unit.execute(new CommandCall(
                "rf02-fail", "库管", "ROUTE_BAG_SAVE", "MOTHER_ORDER", target, "fail"), session -> {
            service.capture(BusinessKind.PROCESSING, "MD-20261030-0203", "牙骨", session);
            bagService.open("MD-20261030-0203", "牙骨", "REQ-1", "LINE-2", new BigDecimal("4"), StockMeasurement.unknown(), session);
            throw new IllegalStateException("失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(new JdbcRouteSnapshotRepository(dataSource).list(BusinessKind.PROCESSING, "MD-20261030-0203")).isEmpty();
        assertThat(new JdbcActivityBagRepository(dataSource).listByTarget("MD-20261030-0203")).isEmpty();
        assertThat(new JdbcRouteSnapshotRepository(dataSource).find(snapshotId)).isPresent();
    }

    private static ApplicationContextRunner runner(DataSource dataSource) {
        return new ApplicationContextRunner()
                .withUserConfiguration(AssemblyProbe.class)
                .withBean(DataSource.class, () -> dataSource);
    }

    private static JdbcDataSource database() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : List.of("V44__m3_command_request.sql", "V80__m3_route_snapshot.sql", "V81__m3_activity_bag.sql")) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql", file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }

    @Configuration
    @Import({
            JdbcActivityBagRepository.class,
            InMemoryActivityBagRepository.class,
            JdbcRouteSnapshotRepository.class,
            InMemoryRouteSnapshotRepository.class
    })
    static class AssemblyProbe {
    }
}
