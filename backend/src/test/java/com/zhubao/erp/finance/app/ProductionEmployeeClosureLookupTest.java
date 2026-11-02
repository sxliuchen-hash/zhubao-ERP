package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.WholeBagReport;
import com.zhubao.erp.business.infra.InMemoryWholeBagReportRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingCompletionRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingSourceRepository;
import com.zhubao.erp.business.infra.InMemoryQuantityDispositionRepository;
import com.zhubao.erp.business.infra.InMemoryReworkTaskRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.finance.domain.EmployeeClosureKind;
import com.zhubao.erp.finance.domain.EmployeeTargetClosure;
import com.zhubao.erp.finance.domain.EmployeeTargetClosureLookup;
import com.zhubao.erp.finance.domain.EmployeeWorkShare;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionEmployeeClosureLookupTest {

    @Test
    void 未做完的目标拒绝核算完成后按实作入账() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        String target = "MD-20261030-1008";
        documents.saveMotherOrder(mother(target));
        ActivityBag bag = new ActivityBagService(bags).open(
                target, "牙骨", "REQ-1", "LINE-1", new BigDecimal("10"), StockMeasurement.unknown(), null);
        routes.insert(new RouteSnapshot("RS-RF07", BusinessKind.PROCESSING, target, 1, List.of("CUT", "BLANK"), true), null);
        InMemoryWholeBagReportRepository reports = new InMemoryWholeBagReportRepository();
        ProductionEmployeeClosureLookup lookup = new ProductionEmployeeClosureLookup(bags, routes, portions, new InMemoryReworkTaskRepository(), new InMemoryQuantityDispositionRepository(), documents, reports, new InMemoryOutsourcingCompletionRepository());
        EmployeeTargetClosure open = lookup.find(target).orElseThrow();
        assertThat(open.closure()).isEqualTo(EmployeeClosureKind.NOT_CLOSED);
        assertThat(open.lossQuantity()).isNull();

        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        EmployeeTargetSettlementService settlement = new EmployeeTargetSettlementService(
                lookup, finance, actor -> { }, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        OperatorContext actor = new OperatorContext("核算员", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        EmployeeWorkShare share = new EmployeeWorkShare("payee-a", "切料", new BigDecimal("10"), new BigDecimal("2"), false);
        assertThatThrownBy(() -> settlement.settle(actor, "rf07-open01", target, 7L, List.of(share)))
                .hasMessage("加工目标尚未闭合，不能核算工资");

        portions.save(new NodePortionBalance(bag.id(), "CUT", new BigDecimal("10"), new BigDecimal("10")), null);
        portions.save(new NodePortionBalance(bag.id(), "BLANK", new BigDecimal("10"), new BigDecimal("10")), null);
        reports.save(new WholeBagReport("BR-cut", "report-cut", bag.id(), 1, target, "CUT", new BigDecimal("10"), "ATT", "甲", null, "2026-11-01", "payee-a"), null);
        reports.save(new WholeBagReport("BR-blank", "report-blank", bag.id(), 2, target, "BLANK", new BigDecimal("10"), "ATT", "乙", null, "2026-11-01", "payee-b"), null);
        EmployeeTargetClosure closed = lookup.find(target).orElseThrow();
        assertThat(closed.closure()).isEqualTo(EmployeeClosureKind.CLOSED_NORMAL);
        assertThat(closed.employeeQuantity()).isEqualByComparingTo("20");
        assertThat(closed.outsourcingQuantity()).isEqualByComparingTo("0");
        assertThat(closed.quantityUnit()).isEqualTo("颗");
        assertThat(settlement.settle(actor, "rf07-done01", target, 7L, List.of(share, new EmployeeWorkShare("payee-b", "BLANK", new BigDecimal("10"), new BigDecimal("3"), false)))).hasSize(2);
        assertThat(finance.listPayrollLines()).hasSize(2);
        assertThat(lookup.find("MD-20261030-1999")).isEmpty();
    }

    @Test
    void 应用装配使用真实闭合查询() {
        new ApplicationContextRunner()
                .withUserConfiguration(AssemblyProbe.class)
                .run(context -> assertThat(context.getBean(EmployeeTargetClosureLookup.class))
                        .isInstanceOf(ProductionEmployeeClosureLookup.class));
    }

    private static ProductionEmployeeClosureLookup lookup(InMemoryActivityBagRepository bags,
                                                          InMemoryRouteSnapshotRepository routes,
                                                          InMemoryNodePortionReader portions,
                                                          InMemoryBusinessDocumentRepository documents) {
        return new ProductionEmployeeClosureLookup(
                bags, routes, portions, new InMemoryReworkTaskRepository(),
                new InMemoryOutsourcingSourceRepository(), new InMemoryOutsourcingDispatchRepository(),
                new InMemoryQuantityDispositionRepository(), documents);
    }

    static MotherOrderRecord mother(String code) {
        return new MotherOrderRecord(code, 7L, null, "牙骨", "2026-10-30", "", "", "", "", "",
                "手串", "CUSTOMER", true, "WH", new BigDecimal("10"), "颗", null,
                "RAW_WAITING", "", null, null, null, "收货员", "", "", "OPEN", "2026-10-30 08:00:00",
                "", "2026-11-30", "恒信");
    }

    @Configuration
    @Import({
            ProductionEmployeeClosureLookup.class,
            InMemoryActivityBagRepository.class,
            InMemoryRouteSnapshotRepository.class,
            InMemoryNodePortionReader.class,
            InMemoryReworkTaskRepository.class,
            InMemoryOutsourcingSourceRepository.class,
            InMemoryOutsourcingDispatchRepository.class,
            InMemoryOutsourcingReturnRepository.class,
            InMemoryQuantityDispositionRepository.class,
            InMemoryBusinessDocumentRepository.class,
            InMemoryWholeBagReportRepository.class,
            InMemoryOutsourcingCompletionRepository.class
    })
    static class AssemblyProbe {
    }
}
