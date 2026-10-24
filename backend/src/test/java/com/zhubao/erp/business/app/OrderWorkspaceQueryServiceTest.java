package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OrderWorkspaceAccess;
import com.zhubao.erp.business.domain.OrderWorkspacePage;
import com.zhubao.erp.business.domain.OrderWorkspaceQuery;
import com.zhubao.erp.business.domain.OrderWorkspaceRow;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessProgressHead;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryOrderRequirementRepository;
import com.zhubao.erp.business.infra.InMemoryProcessProgressRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.domain.WorkSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderWorkspaceQueryServiceTest {

    private static final WorkSession SESSION = new WorkSession() {
        @Override
        public Optional<Connection> connection() {
            return Optional.empty();
        }

        @Override
        public void onRollback(Runnable undo) {
        }
    };

    private InMemoryBusinessDocumentRepository documents;
    private InMemoryProcessProgressRepository progress;
    private InMemoryOrderRequirementRepository requirements;
    private OrderWorkspaceQueryService service;
    private final OperatorContext office = new OperatorContext("办公室", RoleCode.WORKSHOP_LEADER, DataScope.ALL);
    private final OrderWorkspaceAccess orders = new OrderWorkspaceAccess(
            List.of("inbound", "progress", "production", "quality", "inventory"), List.of());

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        progress = new InMemoryProcessProgressRepository();
        requirements = new InMemoryOrderRequirementRepository();
        service = new OrderWorkspaceQueryService(documents, null, progress, requirements, null);
    }

    @Test void partialUpstreamIsVisibleToDownstreamAccountWithoutCompletingEitherProcess() {
        documents.saveMotherOrder(mother("MD-BQ", 1001L, "恒信珠宝", "2026-10-23", "10", "件"));
        documents.saveMotherSplitState(new MotherSplitState("MD-BQ", MotherSplitState.SPLIT, "V1", "收货", "2026-10-09T08:00:00"));
        documents.saveChildWorkOrder(child("ZD-BQ", "MD-BQ", 1001L, "恒信珠宝"));
        documents.saveProcessBatch(new com.zhubao.erp.business.domain.ProcessBatchHandoff("PB-Q", "ZD-BQ", "雕刻", BigDecimal.ONE, null, "雕刻工", "2026-10-24T09:00:00"), SESSION);
        var page = service.query(office, new OrderWorkspaceAccess(List.of("production"), List.of("镶嵌")), new OrderWorkspaceQuery("PRODUCTION", "", null, "", "ALL", null, 1, 20));
        assertThat(page.items()).anySatisfy(row -> { assertThat(row.code()).isEqualTo("ZD-BQ"); assertThat(row.currentProcess()).isEqualTo("镶嵌"); assertThat(row.facet()).isEqualTo("IN_PROCESS"); assertThat(row.processFormallyDelivered()).isFalse(); });
    }

    @Test
    void anonymousAndMenusWithoutOrderSourceAreRejected() {
        assertThatThrownBy(() -> service.query(null, orders, query()))
                .isInstanceOf(PermissionDeniedException.class);
        documents.saveMotherOrder(mother("MD-1", 1001L, "恒信珠宝", "2026-10-23", "10.500", "件"));
        assertThatThrownBy(() -> service.query(office, new OrderWorkspaceAccess(List.of("finance"), List.of()), query()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(documents.listMotherOrders()).hasSize(1);
        assertThat(documents.listAllProcessStationHandoffs()).isEmpty();
    }

    @Test
    void emptyCustomerRangeAndConflictingChildDoNotLeak() {
        documents.saveMotherOrder(mother("MD-1", 1001L, "恒信珠宝", "2026-10-23", "1", "件"));
        documents.saveMotherOrder(mother("MD-2", 1002L, "雅辰", "2026-10-23", "1", "件"));
        documents.saveMotherSplitState(new MotherSplitState("MD-1", MotherSplitState.SPLIT, "V1", "收货", "2026-10-09T08:00:00"));
        documents.saveChildWorkOrder(child("ZD-1", "MD-1", 1001L, "恒信珠宝"));
        documents.saveChildWorkOrder(child("ZD-X", "MD-1", 1002L, "另一客户"));

        OperatorContext empty = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE);
        OrderWorkspacePage hidden = service.query(empty, orders, query());
        assertThat(hidden.items()).isEmpty();
        assertThat(hidden.totalRows()).isZero();

        OperatorContext scoped = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of("1001"));
        OrderWorkspacePage page = service.query(scoped, orders, OrderWorkspaceQuery.parse(null, "MD-2", null, null, null, null, null, null));
        assertThat(page.items()).isEmpty();
        assertThat(page.totalRows()).isZero();
        assertThat(service.query(scoped, orders, query()).items())
                .extracting(OrderWorkspaceRow::code)
                .containsExactly("ZD-1");
    }

    @Test
    void splitChildrenAndRedoRoundKeepSeparateIdentities() {
        documents.saveMotherOrder(mother("MD-1", 1001L, "恒信珠宝", "2026-10-20", "2", "件"));
        documents.saveMotherSplitState(new MotherSplitState("MD-1", MotherSplitState.SPLIT, "V1", "收货", "2026-10-09T08:00:00"));
        documents.saveChildWorkOrder(child("ZD-1", "MD-1", 1001L, "恒信珠宝"));
        documents.saveChildWorkOrder(child("ZD-2", "MD-1", 1001L, "恒信珠宝"));
        progress.save(new ProcessProgressHead("ZD-1", "雕刻", 60, 3, "", "王强", "2026-10-20T09:00:00"), SESSION);
        requirements.saveRound(new ExecutionRound(
                "RW-ID", "MD-1", "ZD-1", "RW-1", "抛光", "CHG-1", "ZD-1", "2026-10-21T09:00:00"
        ), SESSION);

        OrderWorkspacePage page = service.query(office, orders, query());
        assertThat(page.items()).extracting(OrderWorkspaceRow::code).containsExactlyInAnyOrder("ZD-1", "ZD-2", "RW-1");
        assertThat(page.items()).extracting(OrderWorkspaceRow::rowKey).doesNotHaveDuplicates();
        assertThat(page.items()).noneMatch(row -> "MD-1".equals(row.code()));
        OrderWorkspaceRow source = page.items().stream().filter(row -> "ZD-1".equals(row.code())).findFirst().orElseThrow();
        OrderWorkspaceRow round = page.items().stream().filter(row -> "RW-1".equals(row.code())).findFirst().orElseThrow();
        assertThat(source.processPercent()).isEqualTo(60);
        assertThat(source.processFormallyDelivered()).isFalse();
        assertThat(source.executionRoundCode()).isNull();
        assertThat(round.executionRoundCode()).isEqualTo("RW-1");
        assertThat(round.workOrderCode()).isEqualTo("ZD-1");
        assertThat(round.processPercent()).isNull();
        assertThat(documents.listAllProcessStationHandoffs()).isEmpty();
    }

    @Test
    void inspectedOrderIsNotReadyToShipUntilFinishedStockExists() {
        documents.saveMotherOrder(mother("MD-Q", 1001L, "恒信珠宝", "2026-10-23", "1", "件"));
        documents.saveMotherSplitState(new MotherSplitState("MD-Q", MotherSplitState.KEEP, "V1", "收货", "2026-10-09T08:00:00"));
        documents.saveQualityRequirementCheck(new QualityRequirementCheck(
                "QC-1", "MD-Q", "MD-Q", "周娜", "合格", false, "", "", "2026-10-23T10:00:00",
                List.of(new QualityCheckLine(1, "抛光", "合格", ""))
        ));

        OrderWorkspaceRow waiting = service.query(office, orders, query()).items().get(0);
        assertThat(waiting.businessStatus()).isEqualTo("已质检");
        assertThat(waiting.facet()).isEqualTo("UNKNOWN");
        assertThat(waiting.facetNote()).contains("放行");

        StockBalance finished = new StockBalance(
                new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, "MD-Q", "MD-Q", "MAT", "RAW", StockStatus.FINISHED),
                new BigDecimal("1"),
                new BigDecimal("1")
        );
        OrderWorkspaceRow ready = service.queryWithBalances(office, orders, query(), List.of(finished)).items().get(0);
        assertThat(ready.facet()).isEqualTo("READY_TO_SHIP");
        assertThat(ready.businessStatus()).isEqualTo("已质检");
    }

    @Test
    void firstProcessProgressChangesProductionStatusButNeverCompletesHandoff() {
        documents.saveMotherOrder(mother("MD-PG", 1001L, "恒信珠宝", "2026-10-23", "1", "件"));
        documents.saveMotherSplitState(new MotherSplitState("MD-PG", MotherSplitState.SPLIT, "V1", "收货", "2026-10-09T08:00:00"));
        documents.saveChildWorkOrder(child("ZD-PG", "MD-PG", 1001L, "恒信珠宝"));
        int version = 0;
        for (int percent : List.of(0, 30, 60, 90, 100)) {
            progress.save(new ProcessProgressHead("ZD-PG", "雕刻", percent, ++version, "", "雕刻员", "2026-10-24T09:00:00"), SESSION);
            OrderWorkspaceRow row = service.query(office, orders, query()).items().get(0);
            assertThat(row.businessStatus()).isEqualTo(percent == 0 ? "待开工" : "在制");
            assertThat(row.facet()).isEqualTo(percent == 0 ? "WAITING_START" : "IN_PROCESS");
            assertThat(row.currentProcess()).isEqualTo("雕刻");
            assertThat(row.processFormallyDelivered()).isFalse();
            assertThat(documents.listAllProcessStationHandoffs()).isEmpty();
        }
        documents.saveProcessStationHandoff(new ProcessStationHandoff("ZD-PG", "雕刻", "雕刻员", "LED-PG", "2026-10-24T10:00:00"));
        OrderWorkspaceRow next = service.query(office, orders, query()).items().get(0);
        assertThat(next.currentProcess()).isEqualTo("镶嵌");
        assertThat(next.processPercent()).isNull();
        assertThat(next.businessStatus()).isEqualTo("在制");
        assertThat(next.processFormallyDelivered()).isFalse();
    }

    @Test
    void shipmentWithoutRemainingStockIsShippedAndQuantitiesStaySeparate() {
        documents.saveMotherOrder(mother("MD-S", 1001L, "恒信珠宝", "2026-10-23", "10.500", "克"));
        documents.saveMotherOrder(mother("MD-P", 1001L, "恒信珠宝", "", "120", "颗"));
        documents.saveOutboundOrder(outbound("FH-1", "MD-S", "MD-S", "SF-1"));

        OrderWorkspacePage page = service.query(office, orders, query());
        OrderWorkspaceRow grams = page.items().stream().filter(row -> "MD-S".equals(row.code())).findFirst().orElseThrow();
        OrderWorkspaceRow pieces = page.items().stream().filter(row -> "MD-P".equals(row.code())).findFirst().orElseThrow();
        assertThat(grams.quantity()).isEqualTo("10.5");
        assertThat(grams.quantityUnit()).isEqualTo("克");
        assertThat(grams.facet()).isEqualTo("SHIPPED");
        assertThat(pieces.quantity()).isEqualTo("120");
        assertThat(pieces.quantityUnit()).isEqualTo("颗");
        assertThat(pieces.facet()).isEqualTo("UNSPLIT");
        assertThat(pieces.materialName()).isNull();
        assertThat(page.items()).extracting(OrderWorkspaceRow::code).containsExactly("MD-S", "MD-P");
    }

    @Test
    void sameWaybillReturnsEachMotherAndFiltersCountBeforePaging() {
        documents.saveMotherOrder(mother("MD-A", 1001L, "锦和", "2026-10-23", "1", "件", "SF-SAME"));
        documents.saveMotherOrder(mother("MD-B", 1001L, "锦和", "2026-10-24", "1", "件", "SF-SAME"));
        documents.saveMotherOrder(mother("MD-C", 1001L, "云璟", "2026-10-01", "1", "件", "SF-OTHER"));

        OrderWorkspacePage waybill = service.query(office, orders, OrderWorkspaceQuery.parse(null, "SF-SAME", null, null, null, null, null, null));
        assertThat(waybill.items()).extracting(OrderWorkspaceRow::code).containsExactly("MD-A", "MD-B");
        assertThat(waybill.totalRows()).isEqualTo(2);

        OrderWorkspacePage due = service.query(office, orders, OrderWorkspaceQuery.parse(
                null, null, null, null, null, "2026-10-23", "1", "1"));
        assertThat(due.totalRows()).isEqualTo(2);
        assertThat(due.items()).extracting(OrderWorkspaceRow::code).containsExactly("MD-C");
        assertThat(due.facetCounts().get("ALL")).isEqualTo(2);
        assertThat(due.facetCounts().get("UNSPLIT")).isEqualTo(2);
        assertThat(service.query(office, orders, OrderWorkspaceQuery.parse(null, null, null, null, "UNSPLIT", null, "1", "20")).items())
                .hasSize(3);
    }

    @Test
    void productionViewKeepsBoundProcessAndRejectsBadParameters() {
        documents.saveMotherOrder(mother("MD-1", 1001L, "恒信珠宝", "2026-10-23", "1", "件"));
        documents.saveMotherSplitState(new MotherSplitState("MD-1", MotherSplitState.SPLIT, "V1", "收货", "2026-10-09T08:00:00"));
        documents.saveChildWorkOrder(new ChildWorkOrderRecord(
                "ZD-1", "MD-1", 1001L, "恒信珠宝", "抛光", "2026-10-23", "MAT", "吊坠",
                BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "镶嵌", "V1", "", "王强", "2026-10-09T08:30:00"
        ));

        OrderWorkspaceAccess polishing = new OrderWorkspaceAccess(List.of("production"), List.of("抛光"));
        assertThat(service.query(office, polishing, OrderWorkspaceQuery.parse("PRODUCTION", null, null, null, null, null, null, null)).items())
                .isEmpty();
        assertThatThrownBy(() -> service.query(office, polishing, OrderWorkspaceQuery.parse("QUALITY", null, null, null, null, null, null, null)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> OrderWorkspaceQuery.parse(null, null, "锦和", null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("档案编号");
        assertThatThrownBy(() -> OrderWorkspaceQuery.parse(null, null, null, null, "LATER", "2026/10/23", "0", "101"))
                .isInstanceOf(BusinessException.class);
    }

    private OrderWorkspaceQuery query() {
        return OrderWorkspaceQuery.parse(null, null, null, null, null, null, null, null);
    }

    private MotherOrderRecord mother(String code, Long customerId, String name, String dueDate, String quantity, String unit) {
        return mother(code, customerId, name, dueDate, quantity, unit, "");
    }

    private MotherOrderRecord mother(
            String code, Long customerId, String name, String dueDate, String quantity, String unit, String courierNo
    ) {
        return new MotherOrderRecord(
                code, customerId, customerId, "MAT", "2026-10-09", "", courierNo, "", "", "",
                "吊坠", "CUSTOMER", true, "RAW", new BigDecimal(quantity), unit, new BigDecimal("1"),
                "RAW_WAITING", "", "", null, "", "收货", "", "", "CONFIRMED", "2026-10-09T08:00:00",
                "抛光", dueDate, name
        );
    }

    private ChildWorkOrderRecord child(String code, String motherCode, Long customerId, String customerName) {
        return new ChildWorkOrderRecord(
                code, motherCode, customerId, customerName, "抛光", "2026-10-23", "MAT", "吊坠",
                BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "雕刻、镶嵌、抛光", "V1", "", "收货", "2026-10-09T08:30:00"
        );
    }

    private OutboundOrderRecord outbound(String code, String motherCode, String workOrderCode, String logisticsNo) {
        return new OutboundOrderRecord(
                code, motherCode, workOrderCode, 1001L, 1001L, "MAT", "RAW", "OUT",
                "2026-10-23", "", BigDecimal.ONE, "克", BigDecimal.ONE, "", logisticsNo, "",
                "", "", null, "", "发货", "", "", "SHIPPED", "L1", "2026-10-23T10:00:00"
        );
    }
}
