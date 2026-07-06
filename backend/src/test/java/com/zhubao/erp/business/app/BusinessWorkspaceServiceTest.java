package com.zhubao.erp.business.app;

import com.zhubao.erp.business.infra.BusinessWorkspaceSeedData;
import com.zhubao.erp.business.infra.InMemoryBusinessWorkspaceRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessWorkspaceServiceTest {

    @Test
    void exposesCoreBusinessWorkspaceBaseline() {
        BusinessWorkspaceService service = new BusinessWorkspaceService(
                new InMemoryBusinessWorkspaceRepository(new BusinessWorkspaceSeedData())
        );

        assertThat(service.summary().inboundOrders()).isEqualTo(4);
        assertThat(service.summary().activeWorkOrders()).isEqualTo(4);
        assertThat(service.summary().pendingQualityCases()).isEqualTo(3);
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
}
