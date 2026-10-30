package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.app.OutsourcingSourceService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.OutsourcingCounterpart;
import com.zhubao.erp.business.domain.OutsourcingParty;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutsourcingTaskRepositoryTest {

    @Test
    void 内存保留名称快照和原权属且不改袋() {
        InMemoryOutsourcingSourceRepository repository = new InMemoryOutsourcingSourceRepository();
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        OutsourcingSourceService service = service(repository, bags, routes,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        Established established = establish(service, bags, routes);

        assertThat(established.task().partyNameSnapshot()).isEqualTo("甲抛光");
        assertThat(established.party().name()).isEqualTo("甲抛光厂");
        assertThat(established.party().version()).isEqualTo(2);
        assertThat(established.task().ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(established.task().ownerRef()).isEqualTo("10086");
        assertThat(established.task().businessKind()).isEqualTo(BusinessKind.PROCESSING);
        assertThat(established.task().nodeId()).isEqualTo("POLISH_BEAD");
        assertThat(established.task().version()).isEqualTo(1);
        assertThat(repository.listByTarget("MD-20261030-0003")).hasSize(1);
        assertThat(repository.findTaskByRequest("d01-open01")).isPresent();
        ActivityBag bag = bags.find(established.bag().id()).orElseThrow();
        assertThat(bag.quantity()).isEqualByComparingTo("10");
        assertThat(bag.version()).isEqualTo(1);
        assertThat(bag.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(bag.weight().value()).isNull();

        OutsourcingTask sampling = service.openTask("d01-open02", "WX-POLISH-01", "DY-20261030-0001", "SAMPLE_MAKE",
                established.samplingBag().id(), OwnerType.COMPANY, "COMPANY", "clerk-01");
        assertThat(sampling.businessKind()).isEqualTo(BusinessKind.SAMPLING);
        assertThat(sampling.targetCode()).isEqualTo("DY-20261030-0001");
        assertThat(sampling.ownerType()).isEqualTo(OwnerType.COMPANY);
        assertThat(sampling.ownerRef()).isEqualTo("COMPANY");
        assertThat(sampling.partyNameSnapshot()).isEqualTo("甲抛光厂");
        assertThat(bags.find(established.samplingBag().id()).orElseThrow().quantity()).isEqualByComparingTo("1");

        assertThatThrownBy(() -> repository.saveTask(established.task(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协任务已存在");
        assertThat(Arrays.stream(OutsourcingParty.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("partyId", "name", "version");
        assertThat(Arrays.stream(OutsourcingTask.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("taskId", "requestId", "partyId", "partyNameSnapshot", "businessKind",
                        "targetCode", "nodeId", "bagId", "ownerType", "ownerRef", "version");
    }

    @Test
    void 客户员工和错来源不能建立外协任务() {
        InMemoryOutsourcingSourceRepository repository = new InMemoryOutsourcingSourceRepository();
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        OutsourcingSourceService service = service(repository, bags, routes,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        ActivityBagService bagService = new ActivityBagService(bags);
        confirm(routes, BusinessKind.PROCESSING, "MD-20261030-0003", "牙骨");
        ActivityBag bag = bagService.open("MD-20261030-0003", "牙骨", "REQ-1", "LINE-1",
                new BigDecimal("10"), StockMeasurement.unknown(), null);
        service.register("d01-reg-01", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "甲抛光", "clerk-01");

        assertThatThrownBy(() -> service.register("d01-cus-01", OutsourcingCounterpart.CUSTOMER, "WX-CUS-01", "某客户", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协方不能登记成客户或员工");
        assertThatThrownBy(() -> service.register("d01-emp-01", OutsourcingCounterpart.EMPLOYEE, "WX-EMP-01", "某员工", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协方不能登记成客户或员工");
        assertThatThrownBy(() -> service.register("d01-num-01", OutsourcingCounterpart.OUTSOURCING_PARTY, "10086", "客户甲", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协方缺少稳定身份");
        assertThatThrownBy(() -> service.register("d01-act-01", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-SAME-01", "同号", "WX-SAME-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("经办人不是外协方");
        assertThatThrownBy(() -> service.openTask("d01-own-01", "WX-POLISH-01", "MD-20261030-0003", "POLISH_BEAD",
                bag.id(), OwnerType.CUSTOMER, "WX-POLISH-01", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协不改变物料权属");
        assertThatThrownBy(() -> service.openTask("d01-gold1", "WX-POLISH-01", "MD-20261030-0003", "POLISH_GOLD",
                bag.id(), OwnerType.CUSTOMER, "10086", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("工序不属于该业务");
        assertThatThrownBy(() -> service.openTask("d01-fake1", "WX-POLISH-01", "MD-20261030-0003", "外协",
                bag.id(), OwnerType.CUSTOMER, "10086", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知工序：外协");

        ActivityBag closed = bagService.open("MD-20261030-0004", "牙骨", "REQ-1", "LINE-1",
                new BigDecimal("4"), StockMeasurement.unknown(), null);
        confirm(routes, BusinessKind.PROCESSING, "MD-20261030-0004", "牙骨");
        bagService.close(closed.id(), null);
        assertThatThrownBy(() -> service.openTask("d01-close1", "WX-POLISH-01", "MD-20261030-0004", "POLISH_BEAD",
                closed.id(), OwnerType.CUSTOMER, "10086", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已关闭的袋不能作为外协任务来源");

        routes.insert(new RouteSnapshot("RS-D01-STRING", BusinessKind.PROCESSING, "MD-20261030-0005", 1,
                java.util.List.of("STRING"), true), null);
        ActivityBag narrow = bagService.open("MD-20261030-0005", "牙骨", "REQ-1", "LINE-1",
                new BigDecimal("2"), StockMeasurement.unknown(), null);
        assertThatThrownBy(() -> service.openTask("d01-route1", "WX-POLISH-01", "MD-20261030-0005", "POLISH_BEAD",
                narrow.id(), OwnerType.CUSTOMER, "10086", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协节点不在已确认路线上");
        assertThat(repository.listByTarget("MD-20261030-0003")).isEmpty();
        assertThat(repository.findParty("WX-CUS-01")).isEmpty();
        assertThat(repository.findParty("WX-EMP-01")).isEmpty();
    }

    @Test
    void jdbc往返一致且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcOutsourcingSourceRepository repository = new JdbcOutsourcingSourceRepository(dataSource);
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        OutsourcingSourceService service = service(repository, bags, routes,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)));
        Established established = establish(service, bags, routes);
        OutsourcingTask reloaded = repository.findTask(established.task().taskId()).orElseThrow();
        assertThat(reloaded.partyNameSnapshot()).isEqualTo("甲抛光");
        assertThat(reloaded.ownerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(reloaded.ownerRef()).isEqualTo("10086");
        assertThat(reloaded.nodeId()).isEqualTo("POLISH_BEAD");
        assertThat(reloaded.businessKind()).isEqualTo(BusinessKind.PROCESSING);
        assertThat(repository.findParty("WX-POLISH-01").orElseThrow().name()).isEqualTo("甲抛光厂");
        assertThat(repository.listByTarget("MD-20261030-0003")).hasSize(1);
        assertThat(bags.find(established.bag().id()).orElseThrow().weight().value()).isNull();

        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        assertThatThrownBy(() -> unit.execute(new CommandCall(
                "d01-dup-01", "clerk-01", "OPEN_OUTSOURCE_TASK", "OUTSOURCE_TASK", reloaded.targetCode(), "dup"
        ), session -> {
            repository.saveTask(reloaded, session);
            return "x";
        })).isInstanceOf(BusinessException.class).hasMessage("外协任务已存在");
        assertThat(repository.listByTarget("MD-20261030-0003")).hasSize(1);

        OutsourcingSourceService failing = service(repository, bags, routes, new JdbcBusinessWorkUnit(
                dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
            throw new IllegalStateException("失败");
        }));
        assertThatThrownBy(() -> failing.register(
                "d01-fail01", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-FAIL-01", "失败厂", "clerk-01"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findParty("WX-FAIL-01")).isEmpty();
    }

    @Test
    void 内存失败不留下外协方() {
        InMemoryOutsourcingSourceRepository repository = new InMemoryOutsourcingSourceRepository();
        OutsourcingSourceService failing = service(repository, new InMemoryActivityBagRepository(),
                new InMemoryRouteSnapshotRepository(), new MemoryBusinessWorkUnit(
                        new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> failing.register(
                "d01-fail02", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-FAIL-02", "失败厂", "clerk-01"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findParty("WX-FAIL-02")).isEmpty();
    }

    private static Established establish(OutsourcingSourceService service, ActivityBagRepository bags,
                                         RouteSnapshotRepository routes) {
        ActivityBagService bagService = new ActivityBagService(bags);
        confirm(routes, BusinessKind.PROCESSING, "MD-20261030-0003", "牙骨");
        confirm(routes, BusinessKind.SAMPLING, "DY-20261030-0001", "");
        ActivityBag bag = bagService.open("MD-20261030-0003", "牙骨", "REQ-1", "LINE-1",
                new BigDecimal("10"), StockMeasurement.unknown(), null);
        ActivityBag samplingBag = bagService.open("DY-20261030-0001", "牙骨", "REQ-1", "LINE-1",
                new BigDecimal("1"), StockMeasurement.unknown(), null);
        OutsourcingParty registered = service.register(
                "d01-reg-01", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "甲抛光", "clerk-01");
        OutsourcingParty replayed = service.register(
                "d01-reg-01", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "甲抛光", "clerk-01");
        assertThat(replayed.partyId()).isEqualTo(registered.partyId());
        OutsourcingTask task = service.openTask("d01-open01", "WX-POLISH-01", "MD-20261030-0003", "POLISH_BEAD",
                bag.id(), OwnerType.CUSTOMER, "10086", "clerk-01");
        OutsourcingParty renamed = service.rename("d01-ren-01", "WX-POLISH-01", 1, "甲抛光厂", "clerk-01");
        OutsourcingTask again = service.openTask("d01-open01", "WX-POLISH-01", "MD-20261030-0003", "POLISH_BEAD",
                bag.id(), OwnerType.CUSTOMER, "10086", "clerk-01");
        assertThat(again.taskId()).isEqualTo(task.taskId());
        assertThat(again.partyNameSnapshot()).isEqualTo("甲抛光");
        assertThatThrownBy(() -> service.rename("d01-ren-02", "WX-POLISH-01", 1, "被拒绝", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协方已被其他人修改，请刷新后重试");
        assertThatThrownBy(() -> service.register(
                "d01-reg-02", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "另一家", "clerk-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协方已存在");
        return new Established(renamed, again, bag, samplingBag);
    }

    private static void confirm(RouteSnapshotRepository routes, BusinessKind kind, String targetCode, String material) {
        routes.insert(new RouteSnapshot("RS-" + targetCode, kind, targetCode, 1,
                ProcessCatalog.defaults(kind, material).stream().map(ProcessStep::id).toList(), true), null);
    }

    private static OutsourcingSourceService service(OutsourcingSourceRepository sources, ActivityBagRepository bags,
                                                    RouteSnapshotRepository routes, BusinessWorkUnit unit) {
        return new OutsourcingSourceService(sources, bags, routes, unit);
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{"V44__m3_command_request.sql", "V88__m3_outsourcing_source.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }

    private record Established(OutsourcingParty party, OutsourcingTask task, ActivityBag bag, ActivityBag samplingBag) {
    }
}
