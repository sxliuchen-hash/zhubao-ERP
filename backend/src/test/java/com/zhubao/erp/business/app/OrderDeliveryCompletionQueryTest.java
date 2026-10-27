package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryOrderRequirementRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDeliveryCompletionQueryTest {

    private InMemoryBusinessDocumentRepository documents;
    private InMemoryStockLedgerRepository stocks;
    private InMemoryOrderRequirementRepository requirements;
    private OrderDeliveryCompletionQuery query;

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        stocks = new InMemoryStockLedgerRepository();
        requirements = new InMemoryOrderRequirementRepository();
        query = new OrderDeliveryCompletionQuery(documents, stocks, requirements);
    }

    @Test
    void keptMotherIsEligibleOnlyAfterFormalHandoffQualityAndNetShipment() {
        deliverMother("MD-1", new BigDecimal("2.000"));
        OrderDeliveryAssessment done = query.assess(OrderDeliveryAssessment.MOTHER, "MD-1");
        assertThat(done.eligible()).isTrue();
        assertThat(done.feeTarget()).isTrue();
        assertThat(done.deliveryState()).isEqualTo(OrderDeliveryAssessment.COMPLETE);
        assertThat(done.deliveryRevision()).isEqualTo(query.assess(OrderDeliveryAssessment.MOTHER, "MD-1").deliveryRevision());
        assertThat(done.laborFacts()).hasSize(WorkshopProcesses.DEFAULT_ORDER.size());
        assertThat(done.laborFacts()).allMatch(Labor -> Labor.stationClose() && Labor.operatorName().equals("交工人"));
    }

    @Test
    void outboundAloneOrZeroFinishedBalanceDoesNotCompleteDelivery() {
        saveMother("MD-1", new BigDecimal("2.000"));
        documents.saveMotherSplitState(keep("MD-1"));
        ship("MD-1", "MD-1", "MAT", "件", new BigDecimal("2.000"), "OUT-1", "L-1");
        OrderDeliveryAssessment shippedOnly = query.assess(OrderDeliveryAssessment.MOTHER, "MD-1");
        assertThat(shippedOnly.eligible()).isFalse();
        assertThat(shippedOnly.blocks()).anyMatch(block -> block.code().equals("PROCESS_OPEN"));
        assertThat(shippedOnly.blocks()).anyMatch(block -> block.code().equals("QUALITY_MISSING"));

        documents = new InMemoryBusinessDocumentRepository();
        stocks = new InMemoryStockLedgerRepository();
        query = new OrderDeliveryCompletionQuery(documents, stocks, requirements);
        saveMother("MD-2", new BigDecimal("2.000"));
        documents.saveMotherSplitState(keep("MD-2"));
        OrderDeliveryAssessment untouched = query.assess(OrderDeliveryAssessment.MOTHER, "MD-2");
        assertThat(untouched.eligible()).isFalse();
        assertThat(untouched.deliveryState()).isEqualTo(OrderDeliveryAssessment.NOT_COMPLETE);
        assertThat(untouched.blocks()).anyMatch(block -> block.code().equals("QTY_SHORT"));
    }

    @Test
    void partialShipmentReturnAndUnitMismatchStayOpen() {
        deliverMother("MD-1", new BigDecimal("2.000"));
        stocks.clear();
        documents = new InMemoryBusinessDocumentRepository();
        stocks = new InMemoryStockLedgerRepository();
        query = new OrderDeliveryCompletionQuery(documents, stocks, requirements);
        saveMother("MD-1", new BigDecimal("2.000"));
        documents.saveMotherSplitState(keep("MD-1"));
        handoffAndQuality("MD-1");
        StockDimension finished = dimension("MD-1", "MD-1", StockStatus.FINISHED);
        StockDimension shipped = dimension("MD-1", "MD-1", StockStatus.SHIPPED);
        stocks.save(ledger("L-IN", "inbound", "IN-1", null, finished, new BigDecimal("2.000")));
        stocks.save(ledger("L-1", "outbound_shipment", "OUT-1", finished, shipped, new BigDecimal("1.000")));
        documents.saveOutboundOrder(outbound("OUT-1", "MD-1", "MD-1", "MAT", "件", new BigDecimal("1.000"), "L-1"));
        OrderDeliveryAssessment partial = query.assess(OrderDeliveryAssessment.MOTHER, "MD-1");
        assertThat(partial.eligible()).isFalse();
        assertThat(partial.blocks()).anyMatch(block -> block.code().equals("QTY_SHORT"));
        assertThat(partial.blocks()).anyMatch(block -> block.code().equals("STOCK_OPEN"));

        stocks.save(ledger("L-2", "outbound_shipment", "OUT-2", finished, shipped, new BigDecimal("1.000")));
        documents.saveOutboundOrder(outbound("OUT-2", "MD-1", "MD-1", "MAT", "件", new BigDecimal("1.000"), "L-2"));
        assertThat(query.assess(OrderDeliveryAssessment.MOTHER, "MD-1").eligible()).isTrue();
        stocks.save(ledger("L-R", "customer_return", "RET-1", shipped, finished, new BigDecimal("1.000")));
        OrderDeliveryAssessment returned = query.assess(OrderDeliveryAssessment.MOTHER, "MD-1");
        assertThat(returned.eligible()).isFalse();
        assertThat(returned.blocks()).anyMatch(block -> block.code().equals("QTY_SHORT"));

        documents.saveOutboundOrder(outbound("OUT-U", "MD-U", "MD-U", "MAT", "克", new BigDecimal("2.000"), "L-U"));
        saveMother("MD-U", new BigDecimal("2.000"));
        documents.saveMotherSplitState(keep("MD-U"));
        handoffAndQuality("MD-U");
        stocks.save(ledger("L-U", "outbound_shipment", "OUT-U", null, dimension("MD-U", "MD-U", StockStatus.SHIPPED), new BigDecimal("2.000")));
        OrderDeliveryAssessment wrongUnit = query.assess(OrderDeliveryAssessment.MOTHER, "MD-U");
        assertThat(wrongUnit.deliveryState()).isEqualTo(OrderDeliveryAssessment.PENDING_REVIEW);
        assertThat(wrongUnit.blocks()).anyMatch(block -> block.code().equals("UNIT_MISMATCH"));
    }

    @Test
    void splitChildrenSettleSeparatelyAndRecycledChildStaysInScope() {
        saveMother("MD-1", new BigDecimal("10.000"));
        documents.saveMotherSplitState(new MotherSplitState("MD-1", MotherSplitState.SPLIT, "", "开单员", "2026-10-27"));
        documents.saveChildWorkOrder(child("ZD-1", new BigDecimal("4.000")));
        handoffAndQuality("ZD-1", List.of("雕刻"));
        ship("MD-1", "ZD-1", "MAT", "件", new BigDecimal("4.000"), "OUT-Z", "L-Z");
        OrderDeliveryAssessment childView = query.assess(OrderDeliveryAssessment.CHILD, "ZD-1");
        assertThat(childView.eligible()).isTrue();
        OrderDeliveryAssessment mother = query.assess(OrderDeliveryAssessment.MOTHER, "MD-1");
        assertThat(mother.feeTarget()).isFalse();
        assertThat(mother.eligible()).isFalse();
        assertThat(mother.blocks()).anyMatch(block -> block.code().equals("UNSPLIT_REMAINDER"));

        documents.recycleChild("ZD-1", "管理员", "2026-10-27", "B1", "回收");
        OrderDeliveryAssessment recycled = query.assess(OrderDeliveryAssessment.CHILD, "ZD-1");
        assertThat(recycled.eligible()).isFalse();
        assertThat(recycled.blocks()).anyMatch(block -> block.code().equals("RECYCLED"));
        assertThat(query.assess(OrderDeliveryAssessment.MOTHER, "MD-1").deliveryState())
                .isEqualTo(OrderDeliveryAssessment.PENDING_REVIEW);
    }

    @Test
    void openExecutionRoundBlocksTheOriginalTarget() {
        deliverMother("MD-1", new BigDecimal("2.000"));
        requirements.saveRound(new ExecutionRound("R1", "MD-1", "MD-1", "MD-R1", "抛光", "C1", "", "2026-10-27"), null);
        OrderDeliveryAssessment open = query.assess(OrderDeliveryAssessment.MOTHER, "MD-1");
        assertThat(open.eligible()).isFalse();
        assertThat(open.blocks()).anyMatch(block -> block.code().equals("ROUND_OPEN"));
        assertThat(open.deliveryRevision()).isNotEqualTo("ignored");
    }

    private void deliverMother(String code, BigDecimal quantity) {
        saveMother(code, quantity);
        documents.saveMotherSplitState(keep(code));
        handoffAndQuality(code);
        ship(code, code, "MAT", "件", quantity, "OUT-" + code, "L-" + code);
    }

    private void saveMother(String code, BigDecimal quantity) {
        documents.saveMotherOrder(new MotherOrderRecord(code, 101L, 101L, "MAT", "2026-10-10", "", "SF-" + code, "",
                "收货员", "拆包员", "戒托", "CUSTOMER", true, "RAW", quantity, "件", new BigDecimal("1.000"),
                "RAW_WAITING", "", "", null, "", "开单员", "", "", "INBOUND_CONFIRMED", "2026-10-10",
                "内壁刻字", "2026-10-20", "真实客户"));
    }

    private void handoffAndQuality(String order) {
        handoffAndQuality(order, WorkshopProcesses.DEFAULT_ORDER);
    }

    private void handoffAndQuality(String order, List<String> processes) {
        for (String process : processes) {
            documents.saveProcessStationHandoff(new ProcessStationHandoff(order, process, "交工人", "HL", "2026-10-26"));
        }
        documents.saveQualityRequirementCheck(new QualityRequirementCheck("QC-" + order, order, "MD-1", "质检员",
                QualityVerdicts.PASS, false, "QL", "", "2026-10-26", List.of()));
    }

    private void ship(String mother, String work, String material, String unit, BigDecimal quantity, String shipment, String ledger) {
        documents.saveOutboundOrder(outbound(shipment, mother, work, material, unit, quantity, ledger));
        stocks.save(ledger(ledger, "outbound_shipment", shipment, null, dimension(mother, work, StockStatus.SHIPPED), quantity));
    }

    private static OutboundOrderRecord outbound(String code, String mother, String work, String material, String unit,
                                               BigDecimal quantity, String ledger) {
        return new OutboundOrderRecord(code, mother, work, 101L, 101L, material, "FG", "CUS", "2026-10-27",
                "成品", quantity, unit, new BigDecimal("0.100"), "", "", "", "", "", null, "", "发货员", "", "",
                "SHIPPED", ledger, "2026-10-27");
    }

    private static StockLedgerEntry ledger(String code, String type, String document, StockDimension from, StockDimension to, BigDecimal quantity) {
        return new StockLedgerEntry(code, Instant.parse("2026-10-27T00:00:00Z"), type, document, from, to,
                quantity, quantity, "测试");
    }

    private static StockDimension dimension(String mother, String work, StockStatus status) {
        return new StockDimension(OwnerType.CUSTOMER, 101L, 101L, mother, work, "MAT", "FG", status);
    }

    private static MotherSplitState keep(String mother) {
        return new MotherSplitState(mother, MotherSplitState.KEEP, "V", "开单员", "2026-10-27");
    }

    private static ChildWorkOrderRecord child(String code, BigDecimal quantity) {
        return new ChildWorkOrderRecord(code, "MD-1", 101L, "真实客户", "内壁刻字", "2026-10-20",
                "MAT", "戒托", quantity, "件", new BigDecimal("1.000"), "RAW", "雕刻", "verify", "ledger", "开单员", "2026-10-10");
    }
}
