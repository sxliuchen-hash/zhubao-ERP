package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.QuantityInput;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.WholeBagReport;
import com.zhubao.erp.business.domain.WholeBagReportCommand;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.business.infra.InMemoryWholeBagReportRepository;
import com.zhubao.erp.business.infra.JdbcActivityBagRepository;
import com.zhubao.erp.business.infra.JdbcNodePortionRepository;
import com.zhubao.erp.business.infra.JdbcWholeBagReportRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.BusinessEvidenceLink;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.InMemoryBusinessEvidenceLinkRepository;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.document.infra.JdbcBusinessEvidenceLinkRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.CoverDuties;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
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

class WholeBagReportServiceTest {

    @Test
    void 正常整袋保存批次并在重试时返回同一批() {
        Fixture fixture = memoryFixture(new BigDecimal("500"));
        WholeBagReportCommand command = command("bag-c02-01", fixture.bag(), "ATT-1", new BigDecimal("500"), null);

        WholeBagReport first = fixture.service().report(command, operator());
        WholeBagReport again = fixture.service().report(command, operator());

        assertThat(again.batchCode()).isEqualTo(first.batchCode());
        assertThat(first.progressPercent()).isNull();
        assertThat(first.quantity()).isEqualByComparingTo("500");
        assertThat(first.stepId()).isEqualTo("CUT");
        assertThat(fixture.reports().findByRequest("bag-c02-01")).isPresent();
        assertThat(fixture.portions().find(fixture.bag().id(), "CUT").orElseThrow().handled()).isEqualByComparingTo("500");
        assertThat(fixture.portions().find(fixture.bag().id(), "BLANK").orElseThrow().arrived()).isEqualByComparingTo("500");
        ActivityBag bag = fixture.bags().find(fixture.bag().id()).orElseThrow();
        assertThat(bag.quantity()).isEqualByComparingTo("500");
        assertThat(bag.version()).isEqualTo(2);
        assertThat(bag.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(fixture.links().findByTarget(EvidenceCodes.MOTHER_ORDER, bag.targetCode()))
                .extracting(BusinessEvidenceLink::operationCode)
                .containsExactly(first.batchCode());
        assertThat(fixture.query().lookup(bag.id(), operator()).stepId()).isEqualTo("BLANK");
        assertThat(fixture.query().lookup(bag.id(), operator()).availableQuantity()).isEqualByComparingTo("500");
        assertThatThrownBy(() -> fixture.service().report(command, new OperatorContext("另一人", RoleCode.OPERATOR, DataScope.ALL)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("该请求不能跨账号重放");
        WholeBagReportCommand changed = command("bag-c02-01", fixture.bag(), "ATT-2", new BigDecimal("500"), null);
        assertThatThrownBy(() -> fixture.service().report(changed, operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThat(fixture.reports().findByRequest("bag-c02-01").orElseThrow().attachmentCode()).isEqualTo("ATT-1");
    }

    @Test
    void 不记百分之百也能交工且记了百分之百也不产生另一笔() {
        Fixture fixture = memoryFixture(new BigDecimal("120"));
        WholeBagReport report = fixture.service().report(
                command("bag-c02-02", fixture.bag(), "ATT-1", new BigDecimal("120"), 100), operator());
        assertThat(report.progressPercent()).isEqualTo(100);
        assertThat(fixture.query().lookup(fixture.bag().id(), operator()).availableQuantity()).isEqualByComparingTo("120");
        assertThat(fixture.query().lookup(fixture.bag().id(), operator()).stepId()).isEqualTo("BLANK");
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().quantity()).isEqualByComparingTo("120");
        assertThat(fixture.reports().findByRequest("bag-c02-02")).isPresent();
    }

    @Test
    void 没有有效照片部分数量和无权限都不留下批次() {
        Fixture fixture = memoryFixture(new BigDecimal("500"));
        assertThatThrownBy(() -> fixture.service().report(
                command("bag-c02-03", fixture.bag(), "", new BigDecimal("500"), null), operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有有效的现场照片");
        fixture.documents().saveAttachment(photo("ATT-OLD", fixture.bag().targetCode(), "image/jpeg", "已上传", null));
        assertThatThrownBy(() -> fixture.service().report(
                command("bag-c02-04", fixture.bag(), "ATT-OLD", new BigDecimal("500"), null), operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("历史照片不能当作本次拍摄");
        fixture.documents().saveAttachment(photo("ATT-OTHER", "MD-20261030-0199", "image/jpeg", "已上传", EvidenceCodes.MOTHER_ORDER));
        assertThatThrownBy(() -> fixture.service().report(
                command("bag-c02-05", fixture.bag(), "ATT-OTHER", new BigDecimal("500"), null), operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("照片不属于当前加工目标");
        assertThatThrownBy(() -> fixture.service().report(
                command("bag-c02-06", fixture.bag(), "ATT-1", new BigDecimal("480"), null), operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("正常整袋报工必须一次提交当前可处理数量");
        assertThatThrownBy(() -> fixture.service().report(new WholeBagReportCommand(
                "bag-c02-07", fixture.bag().id(), fixture.bag().version(), "CUT",
                new QuantityInput(QuantityInput.Source.PHOTO_COUNT, new BigDecimal("500"), new BigDecimal("500"), "COUNT-1"),
                "ATT-1", null), operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("拍照计数尚未接入，请改用手填数量");
        OperatorContext manager = new OperatorContext(
                "业务", RoleCode.BUSINESS_MANAGER, DataScope.ALL, false, "", List.of(), List.of());
        assertThatThrownBy(() -> fixture.service().report(
                command("bag-c02-08", fixture.bag(), "ATT-1", new BigDecimal("500"), null), manager))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("缺少生产报工权限");
        OperatorContext emptyRange = new OperatorContext(
                "业务", RoleCode.BUSINESS_MANAGER, DataScope.CUSTOMER_RANGE, false, "", List.of(), List.of(CoverDuties.REPORT));
        assertThatThrownBy(() -> fixture.service().report(
                command("bag-c02-09", fixture.bag(), "ATT-1", new BigDecimal("500"), null), emptyRange))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("客户范围为空，不能报工");
        assertThat(fixture.portions().size()).isZero();
        assertThat(fixture.reports().findByRequest("bag-c02-03")).isEmpty();
        assertThat(fixture.links().findAll()).isEmpty();
    }

    @Test
    void 命令失败时撤回报工份额和证据() {
        Fixture fixture = memoryFixture(new BigDecimal("500"));
        WholeBagReportService failing = fixture.service(() -> {
            throw new IllegalStateException("失败");
        });
        assertThatThrownBy(() -> failing.report(
                command("bag-c02-10", fixture.bag(), "ATT-1", new BigDecimal("500"), null), operator()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.portions().size()).isZero();
        assertThat(fixture.reports().findByRequest("bag-c02-10")).isEmpty();
        assertThat(fixture.links().findAll()).isEmpty();
        WholeBagReport recovered = fixture.service().report(
                command("bag-c02-10", fixture.bag(), "ATT-1", new BigDecimal("500"), null), operator());
        assertThat(recovered.quantity()).isEqualByComparingTo("500");
    }

    @Test
    void jdbc往返回放且失败回滚() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : List.of("V44__m3_command_request.sql", "V42__m3_business_evidence_link.sql",
                    "V81__m3_activity_bag.sql", "V82__m3_whole_bag_report.sql", "V141__m3_report_worker_identity.sql")) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql", file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    if (!file.startsWith("V141") && item.trim().toLowerCase().startsWith("alter")) {
                        continue;
                    }
                    statement.execute(item);
                }
            }
        }
        JdbcActivityBagRepository bags = new JdbcActivityBagRepository(dataSource);
        JdbcNodePortionRepository portions = new JdbcNodePortionRepository(dataSource);
        JdbcWholeBagReportRepository reports = new JdbcWholeBagReportRepository(dataSource);
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        JdbcBusinessEvidenceLinkRepository links = new JdbcBusinessEvidenceLinkRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        String target = "MD-20261030-0101";
        ActivityBag bag = new ActivityBag("BAG-JDBC", target, "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("500"), StockMeasurement.unknown(), 1);
        unit.execute(new CommandCall("bag-setup1", "操作工", "BAG_SETUP", "MOTHER_ORDER", target, "open"), session -> {
            bags.save(bag, 0, session);
            return "OPENED";
        });
        routes.insert(new RouteSnapshot("RS-1", BusinessKind.PROCESSING, target, 1, List.of("CUT", "BLANK"), true), null);
        documents.saveAttachment(photo("ATT-JDBC", target, "image/jpeg", "已上传", EvidenceCodes.MOTHER_ORDER));
        ProcessAvailabilityQuery query = new ProcessAvailabilityQuery(bags, routes, portions, new PermissionGuard());
        WholeBagReportService service = new WholeBagReportService(
                bags, query, portions, portions, reports, documents, links, unit);
        WholeBagReportCommand command = command("bag-c02-11", bag, "ATT-JDBC", new BigDecimal("500"), 30);
        OperatorContext actualWorker = new OperatorContext("操作工", RoleCode.ADMIN, DataScope.ALL, false, "", List.of(), List.of(), "worker-001", "", "");
        WholeBagReport first = service.report(command, actualWorker);
        WholeBagReport again = service.report(command, actualWorker);
        assertThat(first.workerAccount()).isEqualTo("worker-001");
        assertThat(new JdbcWholeBagReportRepository(dataSource).findByRequest("bag-c02-11").orElseThrow().workerAccount()).isEqualTo("worker-001");
        assertThat(again.batchCode()).isEqualTo(first.batchCode());
        assertThat(reports.findByRequest("bag-c02-11").orElseThrow().progressPercent()).isEqualTo(30);
        assertThat(portions.find(bag.id(), "BLANK").orElseThrow().arrived()).isEqualByComparingTo("500");
        assertThat(bags.find(bag.id()).orElseThrow().weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        ActivityBag second = new ActivityBag("BAG-FAIL", target, "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("8"), StockMeasurement.unknown(), 1);
        unit.execute(new CommandCall("bag-setup2", "操作工", "BAG_SETUP", "MOTHER_ORDER", target, "open-fail"), session -> {
            bags.save(second, 0, session);
            return "OPENED-2";
        });
        documents.saveAttachment(photo("ATT-FAIL", target, "image/jpeg", "已上传", EvidenceCodes.MOTHER_ORDER));
        WholeBagReportService failing = new WholeBagReportService(
                bags, query, portions, portions, reports, documents, links, unit, () -> {
            throw new IllegalStateException("失败");
        });
        assertThatThrownBy(() -> failing.report(
                command("bag-c02-12", second, "ATT-FAIL", new BigDecimal("8"), null), operator()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reports.findByRequest("bag-c02-12")).isEmpty();
        assertThat(portions.find(second.id(), "CUT")).isEmpty();
        assertThat(bags.find(second.id()).orElseThrow().version()).isEqualTo(1);
        assertThat(links.findAll()).extracting(BusinessEvidenceLink::attachmentCode).containsExactly("ATT-JDBC");
    }

    @Test
    void 过期表单不能被解释为下一道且竞争只留下一批() throws Exception {
        Fixture fixture = memoryFixture(new BigDecimal("500"));
        WholeBagReportCommand cutting = command("rf01-cut", fixture.bag(), "ATT-1", new BigDecimal("500"), null);
        WholeBagReportCommand competing = command("rf01-race", fixture.bag(), "ATT-2", new BigDecimal("500"), null);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<Object>> futures = java.util.List.of(
                    pool.submit(() -> {
                        try { return fixture.service().report(cutting, operator()); }
                        catch (RuntimeException ex) { return ex; }
                    }),
                    pool.submit(() -> {
                        try {
                            return fixture.service().report(competing, operator());
                        } catch (RuntimeException ex) {
                            return ex;
                        }
                    }));
            int saved = 0;
            int rejected = 0;
            for (java.util.concurrent.Future<Object> future : futures) {
                Object value = future.get();
                if (value instanceof WholeBagReport report) {
                    saved++;
                    assertThat(report.stepId()).isEqualTo("CUT");
                } else if (value instanceof BusinessException ex) {
                    rejected++;
                    assertThat(ex.getMessage()).isEqualTo("袋已被其他人修改，请刷新后重试");
                } else {
                    throw new AssertionError(value);
                }
            }
            assertThat(saved).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        ActivityBag after = fixture.bags().find(fixture.bag().id()).orElseThrow();
        assertThat(after.version()).isEqualTo(2);
        assertThat(fixture.reports().findByRequest("rf01-cut").isPresent()
                ^ fixture.reports().findByRequest("rf01-race").isPresent()).isTrue();
        assertThat(fixture.portions().find(after.id(), "BLANK").orElseThrow().arrived()).isEqualByComparingTo("500");
        assertThat(fixture.links().findAll()).hasSize(1);

        WholeBagReportCommand staleNext = new WholeBagReportCommand(
                "rf01-blank", after.id(), 1, "BLANK", QuantityInput.manual(new BigDecimal("500")), "ATT-2", null);
        assertThatThrownBy(() -> fixture.service().report(staleNext, operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("袋已被其他人修改，请刷新后重试");
        WholeBagReportCommand staleStep = new WholeBagReportCommand(
                "rf01-stale-step", after.id(), after.version(), "CUT",
                QuantityInput.manual(new BigDecimal("500")), "ATT-2", null);
        assertThatThrownBy(() -> fixture.service().report(staleStep, operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前工序已变化，请刷新后重试");
        assertThat(fixture.reports().findByRequest("rf01-blank")).isEmpty();
        assertThat(fixture.reports().findByRequest("rf01-stale-step")).isEmpty();
        assertThat(fixture.portions().find(after.id(), "BLANK").orElseThrow().handled()).isEqualByComparingTo("0");
        assertThat(fixture.links().findAll()).hasSize(1);

        String winner = fixture.reports().findByRequest("rf01-cut").isPresent() ? "rf01-cut" : "rf01-race";
        String photo = "rf01-cut".equals(winner) ? "ATT-1" : "ATT-2";
        WholeBagReport again = fixture.service().report(
                command(winner, fixture.bag(), photo, new BigDecimal("500"), null), operator());
        assertThat(again.stepId()).isEqualTo("CUT");
        assertThat(again.batchCode()).isEqualTo(fixture.reports().findByRequest(winner).orElseThrow().batchCode());
    }

    private static OperatorContext operator() {
        return new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL);
    }

    private static WholeBagReportCommand command(String requestId, ActivityBag bag, String attachment,
                                                 BigDecimal quantity, Integer progress) {
        return new WholeBagReportCommand(
                requestId, bag.id(), bag.version(), "CUT", QuantityInput.manual(quantity), attachment, progress);
    }

    private static EvidenceAttachment photo(String code, String target, String media, String status, String objectType) {
        return new EvidenceAttachment(
                code, "DOC-" + code, "来料母单", target, code + ".jpg", media,
                "local://evidence/" + code, "hot", "PWA 拍照上传", status,
                "操作工", "sha256:" + code, "2026-10-30 10:00:00", "无", "green", objectType);
    }

    private static Fixture memoryFixture(BigDecimal quantity) {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryWholeBagReportRepository reports = new InMemoryWholeBagReportRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        InMemoryBusinessEvidenceLinkRepository links = new InMemoryBusinessEvidenceLinkRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        ActivityBag bag = new ActivityBagService(bags).open(
                "MD-20261030-0101", "牙骨", "REQ-1", "LINE-1", quantity, StockMeasurement.unknown(), null);
        routes.insert(new RouteSnapshot(
                "RS-1", BusinessKind.PROCESSING, bag.targetCode(), 1, List.of("CUT", "BLANK", "DRILL"), true), null);
        documents.saveAttachment(photo("ATT-1", bag.targetCode(), "image/jpeg", "已上传", EvidenceCodes.MOTHER_ORDER));
        documents.saveAttachment(photo("ATT-2", bag.targetCode(), "image/png", "已上传", EvidenceCodes.MOTHER_ORDER));
        ProcessAvailabilityQuery query = new ProcessAvailabilityQuery(bags, routes, portions, new PermissionGuard());
        WholeBagReportService service = new WholeBagReportService(
                bags, query, portions, portions, reports, documents, links, unit);
        return new Fixture(bags, bag, portions, reports, documents, links, query, service);
    }

    private record Fixture(
            InMemoryActivityBagRepository bags,
            ActivityBag bag,
            InMemoryNodePortionReader portions,
            InMemoryWholeBagReportRepository reports,
            InMemoryDocumentManagementRepository documents,
            InMemoryBusinessEvidenceLinkRepository links,
            ProcessAvailabilityQuery query,
            WholeBagReportService service
    ) {
        private WholeBagReportService service(WholeBagReportService.Probe probe) {
            return new WholeBagReportService(
                    bags, query, portions, portions, reports, documents, links,
                    new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), probe);
        }
    }
}
