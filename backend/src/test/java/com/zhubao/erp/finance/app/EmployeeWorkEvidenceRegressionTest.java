package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.app.*;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.document.domain.*;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.finance.domain.*;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EmployeeWorkEvidenceRegressionTest {
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static OperatorContext worker(String account) { return new OperatorContext(account, RoleCode.ADMIN, DataScope.ALL, false, "", List.of(), List.of(), account, "", ""); }
    @Test void 同节点分次外协只排除对应工序的外协完成份额() {
        Fixture f = new Fixture();
        f.portions.save(new NodePortionBalance(f.bag.id(), "CUT", n("10"), n("10")), null);
        f.portions.save(new NodePortionBalance(f.bag.id(), "BLANK", n("10"), n("10")), null);
        f.report("cut", "CUT", "10", "a"); f.report("blank", "BLANK", "4", "b");
        for (int i=0; i<2; i++) f.external.save(new OutsourcingCompletion("OC" + UUID.randomUUID().toString().replace("-", ""), "external-"+i, "return"+i, "task", "dispatch"+i, f.bag.id(), "BLANK", f.bag.targetCode(), n("3")), null);
        EmployeeTargetClosure result = f.lookup().find(f.bag.targetCode()).orElseThrow();
        assertThat(result.employeeQuantity()).isEqualByComparingTo("14");
        assertThat(result.outsourcingQuantity()).isEqualByComparingTo("6");
        assertThat(result.workEvidenceComplete()).isTrue();
        var service = f.settlement();
        assertThatThrownBy(() -> service.settle(worker("finance"), "wrong-person", f.bag.targetCode(), 7L,
            List.of(new EmployeeWorkShare("b", "CUT", n("10"), n("1"), false), new EmployeeWorkShare("a", "BLANK", n("4"), n("1"), false))))
            .hasMessage("计酬人员或工序没有对应实作依据");
        assertThat(service.settle(worker("finance"), "right-work", f.bag.targetCode(), 7L,
            List.of(new EmployeeWorkShare("a", "CUT", n("10"), n("2"), false), new EmployeeWorkShare("b", "BLANK", n("4"), n("3"), false))))
            .extracting(PayrollLine::quantity).containsExactly(n("10.000"), n("4.000"));
    }
    @Test void 真实处置关闭父袋后仍能核对损耗和作业而全损保留门槛() {
        Fixture f = new Fixture();
        var result = f.dispose("loss-two", "8", "2");
        ActivityBag child = f.bags.find(result.handoffBagId()).orElseThrow();
        f.portions.save(new NodePortionBalance(child.id(), "BLANK", n("8"), n("8")), null);
        f.reports.save(new WholeBagReport("BRblank", "report-blank", child.id(), 1, child.targetCode(), "BLANK", n("8"), "ATT", "b", null, "2026-11-01", "b"), null);
        var closed = f.lookup().find(f.bag.targetCode()).orElseThrow();
        assertThat(closed.lossQuantity()).isEqualByComparingTo("2");
        assertThat(closed.workEvidenceComplete()).isTrue();
        assertThat(closed.employeeQuantity()).isEqualByComparingTo("18");
        Fixture lost = new Fixture(); lost.dispose("loss-all", "0", "10");
        assertThat(lost.lookup().find(lost.bag.targetCode()).orElseThrow().closure()).isEqualTo(EmployeeClosureKind.TOTAL_LOSS);
        assertThatThrownBy(() -> lost.settlement().settle(worker("finance"), "settle-loss", lost.bag.targetCode(), 7L,
            List.of(new EmployeeWorkShare("a", "CUT", n("10"), n("1"), false))))
            .hasMessage("全损计酬尚未核定，不能按 0 或正常完工入账");
    }
    @Test void 实际返工结束仍待核且普通名称和非代报不能绕过() {
        Fixture f = new Fixture();
        var inspections = new InMemoryPortionInspectionRepository(); var guard = new PermissionGuard();
        var inspectionService = new PortionInspectionService(f.bags, inspections, guard, f.unit);
        inspectionService.record("inspect-01", f.bag.id(), 1, "REQ-1", n("8"), n("2"), n("0"), n("0"), List.of("裂纹"), worker("qc"));
        var rework = new ReworkPortionService(f.bags, f.bagService, inspections, inspectionService, f.rework, guard, f.unit);
        rework.open("rework-01", f.bag.id(), 1, 1, worker("qc"));
        for (ActivityBag leaf : f.bagService.activeLeaves(f.bag.targetCode())) for (String step : List.of("CUT", "BLANK"))
            f.portions.save(new NodePortionBalance(leaf.id(), step, leaf.quantity(), leaf.quantity()), null);
        assertThat(f.lookup().find(f.bag.targetCode()).orElseThrow().closure()).isEqualTo(EmployeeClosureKind.REWORK_PAY_PENDING);
        assertThatThrownBy(() -> f.settlement().settle(worker("finance"), "settle-rework", f.bag.targetCode(), 7L,
            List.of(new EmployeeWorkShare("a", "CUT", n("8"), n("1"), false), new EmployeeWorkShare("b", "CUT", n("2"), n("1"), false))))
            .hasMessage("返工计酬尚未核定，不能入账");
        assertThat(f.finance.listPayrollLines()).isEmpty();
    }
    @Test void 部分报工计酬身份和照片可由新建JDBC仓储读回() throws Exception {
        var data = new org.h2.jdbcx.JdbcDataSource();
        data.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        try (var connection = data.getConnection(); var sql = connection.createStatement()) {
            for (String file : List.of("V44__m3_command_request.sql", "V83__m3_quantity_disposition.sql", "V142__m3_disposition_work_evidence.sql"))
                for (String statement : com.zhubao.erp.ops.infra.MigrationCatalog.statements(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/mysql", file)))) sql.execute(statement);
        }
        var unit = new com.zhubao.erp.work.app.JdbcBusinessWorkUnit(data, new com.zhubao.erp.work.infra.JdbcCommandRequestRepository(data));
        var repository = new JdbcQuantityDispositionRepository(data);
        unit.execute(new com.zhubao.erp.work.domain.CommandCall("jdbc-work", "a", "REPORT", "BAG", "BAG", "work"), session -> {
            repository.save(new QuantityDispositionLine("QD1", "jdbc-work", "BAG", "CUT", n("8"), n("2"), n("0"), n("0"), n("0"), n("10"), "worker-a", "员工甲", "ATT"), session);
            return "saved";
        });
        var saved = new JdbcQuantityDispositionRepository(data).listByBag("BAG").get(0);
        assertThat(saved.workQuantity()).isEqualByComparingTo("10");
        assertThat(saved.workerAccount()).isEqualTo("worker-a");
        assertThat(saved.workerName()).isEqualTo("员工甲");
        assertThat(saved.attachmentCode()).isEqualTo("ATT");
    }
    static class Fixture {
        final InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        final ActivityBagService bagService = new ActivityBagService(bags);
        final ActivityBag bag = bagService.open("MD-20261101-5001", "牙骨", "REQ-1", "LINE", n("10"), StockMeasurement.unknown(), null);
        final InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        final InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        final InMemoryWholeBagReportRepository reports = new InMemoryWholeBagReportRepository();
        final InMemoryQuantityDispositionRepository dispositions = new InMemoryQuantityDispositionRepository();
        final InMemoryReworkTaskRepository rework = new InMemoryReworkTaskRepository();
        final InMemoryOutsourcingCompletionRepository external = new InMemoryOutsourcingCompletionRepository();
        final MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        final InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        Fixture() { routes.insert(new RouteSnapshot("RS-1", BusinessKind.PROCESSING, bag.targetCode(), 1, List.of("CUT", "BLANK"), true), null); }
        ProductionEmployeeClosureLookup lookup() { var documents = new InMemoryBusinessDocumentRepository(); documents.saveMotherOrder(ProductionEmployeeClosureLookupTest.mother(bag.targetCode())); return new ProductionEmployeeClosureLookup(bags, routes, portions, rework, dispositions, documents, reports, external); }
        EmployeeTargetSettlementService settlement() { return new EmployeeTargetSettlementService(lookup(), finance, actor -> {}, unit); }
        void report(String id, String step, String qty, String account) { reports.save(new WholeBagReport("BR"+id, "report-"+id, bag.id(), 1, bag.targetCode(), step, n(qty), "ATT", account, null, "2026-11-01", account), null); }
        QuantityDispositionResult dispose(String id, String handoff, String loss) {
            var documents = new InMemoryDocumentManagementRepository();
            documents.saveAttachment(new EvidenceAttachment("ATT", "DOC", "来料母单", bag.targetCode(), "a.jpg", "image/jpeg", "local://a", "hot", "PWA 拍照上传", "已上传", "a", "sha256:a", "2026-11-01 10:00:00", "无", "green", EvidenceCodes.MOTHER_ORDER));
            var query = new ProcessAvailabilityQuery(bags, routes, portions, new PermissionGuard(), dispositions);
            var service = new QuantityDispositionService(bags, bagService, routes, query, portions, portions, dispositions, documents, new StockLedgerService(new InMemoryStockLedgerRepository()), unit);
            return service.dispose(new QuantityDispositionCommand(id, bag.id(), 1, n(handoff), n(loss), n("0"), n("0"), n("0"), "ATT", StockMeasurement.unknown(), null, n("10")), worker("a"));
        }
    }
}
