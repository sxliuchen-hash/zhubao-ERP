package com.zhubao.erp.business.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBusinessWorkspaceRepositoryTest {

    @Test
    void keepsSeedDataBehindRepositoryBoundary() {
        InMemoryBusinessWorkspaceRepository repository = new InMemoryBusinessWorkspaceRepository(
                new BusinessWorkspaceSeedData()
        );

        assertThat(repository.inbound().orders()).hasSize(4);
        assertThat(repository.inbound().orders())
                .anySatisfy(order -> assertThat(order.unpackSigner()).isEqualTo("何琳"));
        assertThat(repository.production().workOrders())
                .anySatisfy(order -> {
                    assertThat(order.process()).isEqualTo("雕刻");
                    assertThat(order.machineNo()).isEqualTo("ENG-03");
                });
        assertThat(repository.production().handoffs())
                .anySatisfy(handoff -> assertThat(handoff.code()).isEqualTo("HO-20260706-021"));
        assertThat(repository.inventory().evidence())
                .anySatisfy(evidence -> assertThat(evidence.title()).isEqualTo("视频证据"));
        assertThat(repository.mobile().capabilities())
                .anySatisfy(capability -> assertThat(capability.label()).isEqualTo("离线草稿"));
    }
}
