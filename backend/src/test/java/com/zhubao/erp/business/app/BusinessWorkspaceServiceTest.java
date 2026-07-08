package com.zhubao.erp.business.app;

import com.zhubao.erp.business.infra.BusinessWorkspaceSeedData;
import com.zhubao.erp.business.infra.InMemoryBusinessWorkspaceRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessWorkspaceServiceTest {

    @Test
    void exposesCoreBusinessWorkspaceBaseline() {
        BusinessWorkspaceService service = new BusinessWorkspaceService(
                new InMemoryBusinessWorkspaceRepository(new BusinessWorkspaceSeedData())
        );

        assertThat(service.summary().inboundOrders()).isEqualTo(4);
        assertThat(service.summary().activeWorkOrders()).isEqualTo(5);
        assertThat(service.summary().pendingQualityCases()).isEqualTo(3);
        assertThat(service.inbound().orders())
                .anySatisfy(order -> {
                    assertThat(order.expressCompany()).isEqualTo("顺丰");
                    assertThat(order.unpackSigner()).isEqualTo("何琳");
                });
        assertThat(service.production().workOrders())
                .anySatisfy(order -> {
                    assertThat(order.process()).isEqualTo("雕刻");
                    assertThat(order.machineNo()).isEqualTo("ENG-03");
                    assertThat(order.handoffAuditStatus()).contains("操机签字");
                });
        assertThat(service.inbound().risks())
                .anySatisfy(risk -> assertThat(risk.title()).contains("拆包视频截取失败"));
        assertThat(service.production().handoffs())
                .anySatisfy(handoff -> assertThat(handoff.status()).isEqualTo("审批阻断"));
        assertThat(service.quality().lossApprovals())
                .anySatisfy(approval -> assertThat(approval.code()).isEqualTo("LOSS-20260706-009"));
        assertThat(service.inventory().ledgerEntries())
                .anySatisfy(entry -> assertThat(entry.sourceDocument()).isEqualTo("MD-20260706-001"));
        assertThat(service.mobile().offlineDrafts())
                .anySatisfy(draft -> assertThat(draft.code()).isEqualTo("DRAFT-QC-003"));
    }

    @Test
    void filtersWorkspaceDataByOperatorDataScope() {
        BusinessWorkspaceService service = new BusinessWorkspaceService(
                new InMemoryBusinessWorkspaceRepository(new BusinessWorkspaceSeedData())
        );
        OperatorContext receiverSelf = new OperatorContext(
                "库管员 何琳",
                RoleCode.WAREHOUSE_KEEPER,
                DataScope.SELF
        );

        assertThat(service.inbound(receiverSelf).orders())
                .extracting("code")
                .containsExactly("MD-20260706-001", "MD-20260706-002", "MD-20260706-004");
        assertThat(service.inbound(receiverSelf).orders())
                .noneSatisfy(order -> assertThat(order.customer()).isEqualTo("璟瑞工贸"));
        assertThat(service.inventory(receiverSelf).ledgerEntries())
                .extracting("code")
                .containsExactly("LEDGER-20260706-001");
        assertThat(service.summary(receiverSelf).inboundOrders()).isEqualTo(3);
        assertThat(service.summary(receiverSelf).activeWorkOrders()).isZero();
        assertThat(service.summary(receiverSelf).inventoryLedgerEntries()).isEqualTo(1);
    }

    @Test
    void filtersCustomerScopedWorkspaceDataByAuthorizedCustomerHeader() {
        BusinessWorkspaceService service = new BusinessWorkspaceService(
                new InMemoryBusinessWorkspaceRepository(new BusinessWorkspaceSeedData())
        );
        OperatorContext customerScope = new OperatorContext(
                "sales-review",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                "",
                List.of("恒信珠宝")
        );

        assertThat(service.inbound(customerScope).orders())
                .extracting("code")
                .containsExactly("MD-20260706-001");
        assertThat(service.production(customerScope).workOrders())
                .extracting("code")
                .containsExactly("ZD-20260706-001", "ZD-20260706-002", "ZD-20260706-009");
        assertThat(service.inventory(customerScope).balances())
                .extracting("batch")
                .containsExactly("MD-20260706-001", "ZD-20260706-001");
        assertThat(service.summary(customerScope).inboundOrders()).isEqualTo(1);
        assertThat(service.summary(customerScope).activeWorkOrders()).isEqualTo(3);
    }
}
