package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.ProcessAvailableQuantity;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BagSplitServiceTest {

    @Test
    void 分袋数量守恒且原请求不再次分袋() {
        InMemoryActivityBagRepository repository = new InMemoryActivityBagRepository();
        ActivityBagService bags = new ActivityBagService(repository);
        ActivityBag parent = bags.open(
                "ZD-20261030-0009", "AU9999", "REQ-1", "LINE-1",
                new BigDecimal("10"), StockMeasurement.unknown(), null);
        BagSplitService service = new BagSplitService(
                repository, bags, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));

        String first = service.split("split-001", "wh", parent.id(), List.of(new BigDecimal("4"), new BigDecimal("6")));
        String replay = service.split("split-001", "wh", parent.id(), List.of(new BigDecimal("4"), new BigDecimal("6")));

        assertThat(replay).isEqualTo(first);
        assertThat(parent.id()).hasSize(40);
        assertThat(first.split(",")).allSatisfy(id -> {
            assertThat(id).hasSize(40);
            assertThat(repository.find(id).orElseThrow().parentId()).isEqualTo(parent.id());
        });
        assertThat(repository.find(parent.id()).orElseThrow().closed()).isTrue();
        assertThat(bags.activeLeaves(parent.targetCode())).hasSize(2);
        assertThat(bags.activeLeaves(parent.targetCode()).stream().map(ActivityBag::quantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10");
        assertThatThrownBy(() -> service.split("split-002", "wh", parent.id(), List.of(new BigDecimal("10"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 父袋停在取胚时子袋不回到切料() {
        InMemoryActivityBagRepository repository = new InMemoryActivityBagRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        ActivityBagService bagService = new ActivityBagService(repository);
        ActivityBag parent = bagService.open(
                "MD-20261030-0505", "牙骨", "REQ-1", "LINE-1", new BigDecimal("10"), StockMeasurement.unknown(), null);
        routes.insert(new RouteSnapshot(
                "RS-RF05", BusinessKind.PROCESSING, parent.targetCode(), 1, List.of("CUT", "BLANK"), true), null);
        portions.save(new NodePortionBalance(parent.id(), "CUT", new BigDecimal("10"), new BigDecimal("10")), null);
        portions.save(new NodePortionBalance(parent.id(), "BLANK", new BigDecimal("10"), BigDecimal.ZERO), null);
        BagSplitService service = new BagSplitService(
                repository, bagService, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), portions, portions);
        String split = service.split("rf05-split", "wh", parent.id(), List.of(new BigDecimal("4"), new BigDecimal("6")));
        ProcessAvailabilityQuery query = new ProcessAvailabilityQuery(repository, routes, portions, new PermissionGuard());
        OperatorContext operator = new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL);
        BigDecimal blankArrived = BigDecimal.ZERO;
        BigDecimal cutHandled = BigDecimal.ZERO;
        for (String id : split.split(",")) {
            ProcessAvailableQuantity view = query.lookup(id, operator);
            assertThat(view.stepId()).isEqualTo("BLANK");
            assertThat(view.availableQuantity()).isEqualByComparingTo(repository.find(id).orElseThrow().quantity());
            blankArrived = blankArrived.add(portions.find(id, "BLANK").orElseThrow().arrived());
            cutHandled = cutHandled.add(portions.find(id, "CUT").orElseThrow().handled());
        }
        assertThat(blankArrived).isEqualByComparingTo("10");
        assertThat(cutHandled).isEqualByComparingTo("10");
        assertThat(query.lookup(parent.id(), operator).availableQuantity()).isEqualByComparingTo("0");
        assertThat(portions.list(parent.id())).hasSize(2);
    }

    @Test
    void 真实自动袋号可以再分袋且失败不留下子袋() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : List.of("V44__m3_command_request.sql", "V81__m3_activity_bag.sql")) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql", file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        JdbcActivityBagRepository repository = new JdbcActivityBagRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        ActivityBagService bags = new ActivityBagService(repository);
        BagSplitService service = new BagSplitService(repository, bags, unit);
        String parentId = unit.execute(new CommandCall(
                "rf03-open", "wh", "BAG_OPEN", "BAG", "ZD-RF03", "open"), session -> bags.open(
                "ZD-RF03", "牙骨", "REQ-1", "LINE-1", new BigDecimal("10"), StockMeasurement.unknown(), session).id());
        ActivityBag parent = repository.find(parentId).orElseThrow();
        assertThat(parent.id()).hasSize(40);

        String first = service.split("rf03-split", "wh", parent.id(), List.of(new BigDecimal("4"), new BigDecimal("6")));
        assertThat(service.split("rf03-split", "wh", parent.id(), List.of(new BigDecimal("4"), new BigDecimal("6"))))
                .isEqualTo(first);
        String[] children = first.split(",");
        assertThat(children).hasSize(2);
        assertThat(children).allSatisfy(id -> {
            ActivityBag child = repository.find(id).orElseThrow();
            assertThat(id).hasSize(40);
            assertThat(child.parentId()).isEqualTo(parent.id());
            assertThat(child.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        });
        String again = service.split("rf03-again", "wh", children[0], List.of(new BigDecimal("1"), new BigDecimal("3")));
        assertThat(again.split(",")).allSatisfy(id -> {
            assertThat(repository.find(id).orElseThrow().parentId()).isEqualTo(children[0]);
        });
        assertThat(repository.find(parent.id()).orElseThrow().closed()).isTrue();
        assertThat(repository.find(children[0]).orElseThrow().closed()).isTrue();
        assertThat(bags.activeLeaves(parent.targetCode())).hasSize(3);

        String otherId = unit.execute(new CommandCall(
                "rf03-open-2", "wh", "BAG_OPEN", "BAG", "ZD-RF03", "open-2"), session -> bags.open(
                "ZD-RF03", "牙骨", "REQ-1", "LINE-2", new BigDecimal("4"), StockMeasurement.unknown(), session).id());
        assertThatThrownBy(() -> unit.execute(new CommandCall(
                "rf03-fail", "wh", "BAG_SPLIT", "BAG", otherId, "fail"), session -> {
            service.splitWithin(otherId, List.of(new BigDecimal("2"), new BigDecimal("2")), session);
            throw new IllegalStateException("失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.find(otherId).orElseThrow().closed()).isFalse();
        assertThat(repository.listByTarget("ZD-RF03").stream().filter(bag -> otherId.equals(bag.parentId()))).isEmpty();
    }
}
