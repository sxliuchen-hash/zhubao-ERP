package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.BusinessWorkspaceSeedData;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessWorkspaceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormalBusinessWorkspaceQueryTest {
    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final StockLedgerService stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
    private final ApprovalService approvals = new ApprovalService(new InMemoryApprovalTaskRepository());
    private final InMemoryBusinessWorkspaceRepository demo = new InMemoryBusinessWorkspaceRepository(new BusinessWorkspaceSeedData());
    private final BusinessWorkspaceService service = new BusinessWorkspaceService(demo, documents, new ProgressBoardService(documents), stocks, approvals, false);

    @Test
    void defaultEmptyWorkspaceDoesNotExposeReviewSeedOrWriteRecords() {
        assertThat(service.summary().dataSource()).isEqualTo("LIVE");
        assertThat(service.summary().inboundOrders()).isZero();
        assertThat(service.production().workOrders()).isEmpty();
        assertThat(service.quality().cases()).isEmpty();
        assertThat(service.inventory().balances()).isEmpty();
        assertThat(service.mobile().tasks()).isEmpty();
        assertThat(service.mobile().offlineDrafts()).isEmpty();
        assertThat(stocks.ledgers()).isEmpty();
        assertThat(documents.listMotherOrders()).isEmpty();
    }

    @Test
    void projectsRealReceiptAndCurrentInventoryWithoutMergingDemoOrders() {
        receipt("MD-LIVE", 7001L, "真实客户", "收货员");
        assertThat(service.inbound().orders()).singleElement().satisfies(order -> {
            assertThat(order.code()).isEqualTo("MD-LIVE");
            assertThat(order.customer()).isEqualTo("真实客户");
            assertThat(order.operationContext().customerId()).isEqualTo(7001L);
            assertThat(order.operationContext().materialCode()).isEqualTo("MAT-REAL");
            assertThat(order.operationContext().weight()).isEqualByComparingTo("12.5");
        });
        assertThat(service.inventory().balances()).singleElement().satisfies(balance -> {
            assertThat(balance.operationContext().motherOrderCode()).isEqualTo("MD-LIVE");
            assertThat(balance.operationContext().quantity()).isEqualByComparingTo("2");
        });
        assertThat(service.inventory().ledgerEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.sourceDocument()).isEqualTo("MD-LIVE");
            assertThat(entry.beforeBalance()).isEqualTo("0 / 0 g");
            assertThat(entry.afterBalance()).isEqualTo("2 / 12.5 g");
        });
        assertThat(service.mobile().tasks()).extracting("code").containsExactly("MD-LIVE");
        assertThat(stocks.ledgers()).hasSize(1);
    }

    @Test
    void pendingQualityAndRealDefectsRemainVisibleWithCurrentStockContext() {
        receipt("MD-QC", 7001L, "真实客户", "收货员");
        new MotherOrderSplitService(documents, stocks).keep("MD-QC", "收货员");
        for (String process : WorkshopProcesses.DEFAULT_ORDER) {
            documents.saveProcessStationHandoff(new ProcessStationHandoff("MD-QC", process, "操作员", "", "2026-10-10T08:00:00"));
        }
        StockDimension raw = stocks.balances().get(0).dimension();
        StockDimension semi = new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, "MD-QC", null, "MAT-REAL", "SEMI", StockStatus.SEMI_FINISHED);
        stocks.transfer(new StockTransferCommand("production_report", "MD-QC", raw, semi, new BigDecimal("2"), new BigDecimal("12.5"), "正式报工"));
        assertThat(service.quality().cases()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("待质检");
            assertThat(item.operationContext().warehouseCode()).isEqualTo("SEMI");
        });
        documents.saveQualityRequirementCheck(new QualityRequirementCheck("QC-REAL", "MD-QC", "MD-QC", "质检员", "不合格", true,
                "", "", "2026-10-10T09:00:00", List.of(new QualityCheckLine(1, "无划痕", "不合格", "表面划伤"))));
        assertThat(service.quality().cases()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("QC-REAL");
            assertThat(item.defect()).isEqualTo("无划痕：表面划伤");
        });
        assertThat(service.inventory().balances()).singleElement().satisfies(balance -> assertThat(balance.status()).isEqualTo("待质检"));
        assertThat(stocks.ledgers()).hasSize(2);
    }

    @Test
    void customerScopeUsesExactCustomerIdentityAndEmptyScopeDoesNotGrantAccess() {
        receipt("MD-A", 7001L, "真实客户", "收货员");
        receipt("MD-B", 7002L, "真实客户二", "另一个收货员");
        OperatorContext authorized = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of("7001"));
        assertThat(service.inbound(authorized).orders()).extracting("code").containsExactly("MD-A");
        assertThat(service.inventory(authorized).balances()).extracting("batch").containsExactly("MD-A");
        OperatorContext unassigned = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE);
        assertThat(service.inbound(unassigned).orders()).isEmpty();
        assertThat(service.inventory(unassigned).ledgerEntries()).isEmpty();
    }

    @Test
    void teamAndDepartmentScopesRespectRoleDutiesAndPersonalAssociation() {
        receipt("MD-A", 7001L, "真实客户", "收货员");
        receipt("MD-B", 7002L, "真实客户二", "另一个收货员");
        for (DataScope scope : List.of(DataScope.TEAM, DataScope.DEPARTMENT)) {
            OperatorContext production = new OperatorContext("车间负责人", RoleCode.WORKSHOP_LEADER, scope);
            assertThat(service.inbound(production).orders()).isEmpty();
            assertThat(service.inventory(production).balances()).isEmpty();
            OperatorContext warehouse = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, scope);
            assertThat(service.inbound(warehouse).orders()).hasSize(2);
            assertThat(service.inventory(warehouse).balances()).hasSize(2);
            OperatorContext own = new OperatorContext("收货员", RoleCode.OPERATOR, scope);
            assertThat(service.inbound(own).orders()).extracting("code").containsExactly("MD-A");
        }
    }

    @Test
    void sharedWorkOrderCodeAcrossCustomersIsNotAssignedToTheFirstMother() {
        receipt("MD-A", 7001L, "甲", "收货员");
        receipt("MD-B", 7002L, "乙", "收货员");
        stocks.transfer(new StockTransferCommand("inbound_confirm", "L1", null,
                new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, "MD-A", "ZD-SHARED", "MAT-REAL", "RAW", StockStatus.WIP),
                BigDecimal.ONE, BigDecimal.ONE, "甲"));
        stocks.transfer(new StockTransferCommand("inbound_confirm", "L2", null,
                new StockDimension(OwnerType.CUSTOMER, 7002L, 7002L, "MD-B", "ZD-SHARED", "MAT-REAL", "RAW", StockStatus.WIP),
                BigDecimal.ONE, BigDecimal.ONE, "乙"));
        assertThat(service.production().workOrders()).noneMatch(order -> "ZD-SHARED".equals(order.code()));
        assertThat(service.inventory().balances()).extracting("batch").contains("MD-A", "MD-B");
    }

    @Test
    void ambiguousInventoryDoesNotChooseAnOperationSource() {
        receipt("MD-A", 7001L, "真实客户", "收货员");
        StockDimension otherWarehouse = new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, "MD-A", null, "MAT-REAL", "RAW-OTHER", StockStatus.RAW_WAITING);
        stocks.transfer(new StockTransferCommand("inbound_confirm", "OTHER", null, otherWarehouse, BigDecimal.ONE, BigDecimal.ONE, "另一个实际库位"));
        assertThat(service.inbound().orders()).singleElement().satisfies(order -> assertThat(order.operationContext()).isNull());
        assertThat(service.inventory().balances()).hasSize(2).allSatisfy(balance -> assertThat(balance.operationContext()).isNotNull());
    }

    @Test
    void explicitDemoModeIsLabeledAndDoesNotMergeRealRecords() {
        receipt("MD-LIVE", 7001L, "真实客户", "收货员");
        BusinessWorkspaceService demoService = new BusinessWorkspaceService(demo, documents, new ProgressBoardService(documents), stocks, approvals, true);
        assertThat(demoService.inbound().dataSource()).isEqualTo("DEMO");
        assertThat(demoService.inbound().orders()).hasSize(4).noneMatch(order -> "MD-LIVE".equals(order.code()));
        assertThat(demoService.summary().dataSource()).isEqualTo("DEMO");
    }

    @Test
    void legacyQualityAndReworkCommandsRemainVisibleFromTheirRealLedger() {
        receipt("MD-OLD", 7001L, "真实客户", "收货员");
        StockDimension raw = stocks.balances().get(0).dimension();
        StockDimension wip = new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, "MD-OLD", "ZD-OLD", "MAT-REAL", "RAW", StockStatus.WIP);
        StockDimension semi = new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, "MD-OLD", "ZD-OLD", "MAT-REAL", "SEMI", StockStatus.SEMI_FINISHED);
        stocks.transfer(new StockTransferCommand("work_order_split", "ZD-OLD", raw, wip, new BigDecimal("2"), new BigDecimal("12.5"), "正式拆单"));
        stocks.transfer(new StockTransferCommand("production_report", "ZD-OLD", wip, semi, new BigDecimal("2"), new BigDecimal("12.5"), "生产报工；工序：抛光；操作人：张工"));
        BusinessOperationService operations = new BusinessOperationService(stocks, approvals, documents);
        operations.freezeQualityStock(new FreezeQualityStockCommand("QC-OLD", "MD-OLD", "ZD-OLD", 7001L, 7001L, "MAT-REAL",
                "SEMI", "FROZEN", new BigDecimal("2"), new BigDecimal("12.5"), "抛光", "质检员", "表面划伤", "", ""));
        assertThat(service.quality().cases()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("QC-OLD");
            assertThat(item.defect()).isEqualTo("表面划伤");
            assertThat(item.operationContext().stockStatus()).isEqualTo("FROZEN");
        });
        operations.dispatchRework(new DispatchReworkCommand("FG-OLD", "QC-OLD", "MD-OLD", "ZD-OLD", 7001L, 7001L, "MAT-REAL",
                "FROZEN", "REWORK", new BigDecimal("2"), new BigDecimal("12.5"), "抛光", "返工员", "消除划伤", "", ""));
        assertThat(service.production().workOrders()).singleElement().satisfies(order -> {
            assertThat(order.code()).isEqualTo("ZD-OLD");
            assertThat(order.process()).isEqualTo("抛光");
            assertThat(order.operationContext().stockStatus()).isEqualTo("REWORK");
        });
        assertThat(service.quality().cases()).singleElement().satisfies(item -> assertThat(item.code()).isEqualTo("QC-OLD"));
        assertThat(service.mobile().tasks()).extracting("code").containsExactly("ZD-OLD");
    }

    private void receipt(String code, Long customerId, String customer, String operator) {
        new BusinessOperationService(stocks, approvals, documents).confirmInbound(new ConfirmInboundCommand(code, customerId, customerId,
                "MAT-REAL", "2026-10-10", "", "", "", operator, operator, "真实来料", "", null, "", "RAW",
                new BigDecimal("2"), "件", new BigDecimal("12.5"), operator, "", "", "无划痕", "2099-01-01", customer));
    }
}
