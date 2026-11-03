package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.NodePortionWriter;
import com.zhubao.erp.business.domain.OutsourcingCompletion;
import com.zhubao.erp.business.domain.OutsourcingCompletionRepository;
import com.zhubao.erp.business.domain.OutsourcingCounterpart;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingReturnRepository;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.domain.PortionReleaseRepository;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryOutsourcingCompletionRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingSourceRepository;
import com.zhubao.erp.business.infra.InMemoryPortionReleaseRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.business.infra.JdbcNodePortionRepository;
import com.zhubao.erp.business.infra.JdbcOutsourcingCompletionRepository;
import com.zhubao.erp.business.infra.JdbcOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.JdbcOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.JdbcOutsourcingSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasis;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasisRepository;
import com.zhubao.erp.finance.infra.InMemoryOutsourcingFeeBasisRepository;
import com.zhubao.erp.finance.infra.JdbcOutsourcingFeeBasisRepository;
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

class OutsourcingCompletionSourceTest {

    @Test
    void 实收只确认一次且不放行不记金额() {
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryPortionReleaseRepository releases = new InMemoryPortionReleaseRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryOutsourcingCompletionRepository completions = new InMemoryOutsourcingCompletionRepository();
        InMemoryOutsourcingFeeBasisRepository fees = new InMemoryOutsourcingFeeBasisRepository();
        Workshop workshop = memory(portions, portions, releases, routes, completions, fees);
        OutsourcingReturn returned = receive(workshop, "BAG-C04", "MD-20261030-0401",
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"));
        OutsourcingCompletion completion = workshop.completion().confirm("d04-ok-010", "clerk-01", returned.returnId());
        OutsourcingCompletion replay = workshop.completion().confirm("d04-ok-010", "clerk-01", returned.returnId());
        assertThat(replay.completionId()).isEqualTo(completion.completionId());
        assertThat(completion.quantity()).isEqualByComparingTo("150");
        assertThat(completion.nodeId()).isEqualTo("POLISH_BEAD");
        assertThat(completion.bagId()).isEqualTo(returned.receivedBagId());
        assertThat(portions.find(returned.receivedBagId(), "POLISH_BEAD").orElseThrow().handled()).isEqualByComparingTo("150");
        assertThat(portions.find(returned.receivedBagId(), "GROOVE").orElseThrow().arrived()).isEqualByComparingTo("150");
        assertThat(portions.find(returned.receivedBagId(), "GROOVE").orElseThrow().handled()).isEqualByComparingTo("0");
        assertThat(portions.find(returned.remainderBagId(), "POLISH_BEAD")).isEmpty();
        assertThat(portions.find(returned.remainderBagId(), "GROOVE")).isEmpty();
        assertThat(releases.find(returned.receivedBagId())).isEmpty();
        OutsourcingFeeBasis basis = fees.findByCompletion(completion.completionId()).orElseThrow();
        assertThat(basis.quantity()).isEqualByComparingTo("150");
        assertThat(basis.partyId()).isEqualTo("WX-POLISH-01");
        assertThat(Arrays.stream(OutsourcingFeeBasis.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("basisId", "completionId", "taskId", "partyId", "targetCode", "nodeId", "quantity", "amount");
        assertThat(basis.amount()).isNull();
        assertThat(workshop.returnService().balance(returned.dispatchId()).stillOut()).isEqualByComparingTo("45");
        assertThatThrownBy(() -> workshop.completion().confirm("d04-ok-011", "clerk-01", returned.returnId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协完成量已记录");
        assertThat(portions.find(returned.receivedBagId(), "POLISH_BEAD").orElseThrow().handled()).isEqualByComparingTo("150");

        OutsourcingReturn raw = receive(workshop, "BAG-C4B", "MD-20261030-0402",
                BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO);
        assertThatThrownBy(() -> workshop.completion().confirm("d04-raw-01", "clerk-01", raw.returnId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未加工退回不能记成外协完工");
        assertThat(portions.find(raw.unprocessedBagId(), "POLISH_BEAD")).isEmpty();
        assertThat(portions.find(raw.unprocessedBagId(), "GROOVE")).isEmpty();
        assertThat(completions.findByReturn(raw.returnId())).isEmpty();
        assertThat(fees.findByCompletion("missing")).isEmpty();
    }

    @Test
    void jdbc回放和失败不留下完成量或费用依据() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcNodePortionRepository portions = new JdbcNodePortionRepository(dataSource);
        InMemoryPortionReleaseRepository releases = new InMemoryPortionReleaseRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        JdbcOutsourcingCompletionRepository completions = new JdbcOutsourcingCompletionRepository(dataSource);
        JdbcOutsourcingFeeBasisRepository fees = new JdbcOutsourcingFeeBasisRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        Workshop workshop = new Workshop(
                new JdbcActivityBagRepository(dataSource),
                new JdbcOutsourcingSourceRepository(dataSource),
                new JdbcOutsourcingDispatchRepository(dataSource),
                new JdbcOutsourcingReturnRepository(dataSource),
                portions, portions, releases, routes, completions, fees, unit);
        OutsourcingReturn returned = receive(workshop, "BAG-J04", "MD-20261030-0403",
                new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("5"));
        OutsourcingCompletion first = workshop.completion().confirm("d04-jdbc01", "clerk-01", returned.returnId());
        OutsourcingCompletion replay = workshop.completion().confirm("d04-jdbc01", "clerk-01", returned.returnId());
        assertThat(replay.completionId()).isEqualTo(first.completionId());
        assertThat(fees.findByCompletion(first.completionId()).orElseThrow().quantity()).isEqualByComparingTo("150");
        assertThat(portions.find(returned.receivedBagId(), "POLISH_BEAD").orElseThrow().handled()).isEqualByComparingTo("150");
        assertThat(releases.find(returned.receivedBagId())).isEmpty();

        OutsourcingReturn other = receive(workshop, "BAG-J4B", "MD-20261030-0404",
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO);
        OutsourcingCompletionService failing = new OutsourcingCompletionService(
                workshop.returns, workshop.dispatches, workshop.sources, completions, fees, workshop.bags,
                routes, portions, portions, releases,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }));
        assertThatThrownBy(() -> failing.confirm("d04-fail01", "clerk-01", other.returnId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(completions.findByReturn(other.returnId())).isEmpty();
        assertThat(portions.find(other.receivedBagId(), "POLISH_BEAD")).isEmpty();
    }

    private static OutsourcingReturn receive(Workshop workshop, String bagId, String target,
                                             BigDecimal received, BigDecimal unprocessed, BigDecimal loss) {
        workshop.routes.insert(new RouteSnapshot("RS-" + target, BusinessKind.PROCESSING, target, 1,
                ProcessCatalog.defaults(BusinessKind.PROCESSING, "牙骨").stream().map(ProcessStep::id).toList(), true), null);
        ActivityBag bag = new ActivityBag(bagId, target, "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("200"), StockMeasurement.unknown(), 1);
        workshop.unit.execute(new CommandCall("bag-" + bagId, "clerk-01", "SAVE_BAG", "BAG", bagId, bagId), session -> {
            workshop.bags.save(bag, 0, session);
            return bag.id();
        });
        OutsourcingSourceService sources = new OutsourcingSourceService(workshop.sources, workshop.bags, workshop.routes, workshop.unit);
        if (workshop.sources.findParty("WX-POLISH-01").isEmpty()) {
            sources.register("reg-" + bagId, OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "甲抛光", "clerk-01");
        }
        OutsourcingTask task = sources.openTask("task-" + bagId, "WX-POLISH-01", target, "POLISH_BEAD",
                bagId, OwnerType.CUSTOMER, "10086", "clerk-01");
        var dispatch = new OutsourcingDispatchService(
                workshop.sources, workshop.dispatches, workshop.returns, workshop.bags,
                new BagSplitService(workshop.bags, new ActivityBagService(workshop.bags), workshop.unit), workshop.unit)
                .dispatch("dsp-" + bagId, "clerk-01", task.taskId(), bagId, 1, new BigDecimal("200"));
        return workshop.returnService().receive("ret-" + bagId, "clerk-01", dispatch.dispatchId(), 1,
                received, unprocessed, loss, null);
    }

    private static Workshop memory(NodePortionReader reader, NodePortionWriter writer, PortionReleaseRepository releases,
                                   InMemoryRouteSnapshotRepository routes, OutsourcingCompletionRepository completions,
                                   OutsourcingFeeBasisRepository fees) {
        return new Workshop(new InMemoryActivityBagRepository(), new InMemoryOutsourcingSourceRepository(),
                new InMemoryOutsourcingDispatchRepository(), new InMemoryOutsourcingReturnRepository(),
                reader, writer, releases, routes, completions, fees,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V81__m3_activity_bag.sql", "V82__m3_whole_bag_report.sql", "V141__m3_report_worker_identity.sql",
                    "V88__m3_outsourcing_source.sql", "V89__m3_outsourcing_dispatch.sql",
                    "V90__m3_outsourcing_return.sql", "V91__m3_outsourcing_completion.sql",
                    "V144__m3_outsourcing_fee_amount.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }

    private record Workshop(ActivityBagRepository bags, OutsourcingSourceRepository sources,
                            OutsourcingDispatchRepository dispatches, OutsourcingReturnRepository returns,
                            NodePortionReader portionReader, NodePortionWriter portionWriter,
                            PortionReleaseRepository releases, RouteSnapshotRepository routes,
                            OutsourcingCompletionRepository completionRows, OutsourcingFeeBasisRepository fees,
                            BusinessWorkUnit unit) {
        OutsourcingCompletionService completion() {
            return new OutsourcingCompletionService(returns, dispatches, sources, completionRows, fees, bags, routes,
                    portionReader, portionWriter, releases, unit);
        }

        OutsourcingReturnService returnService() {
            return new OutsourcingReturnService(dispatches, returns, sources, bags,
                    new BagSplitService(bags, new ActivityBagService(bags), unit), unit);
        }
    }
}
