package com.zhubao.erp.sample.app;

import com.zhubao.erp.sample.domain.SampleScenarioStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleScenarioServiceTest {

    @Test
    void exposesReusableAcceptanceSamples() {
        SampleScenarioService service = new SampleScenarioService();

        assertThat(service.findAll()).hasSize(4);
        assertThat(service.readyCount()).isEqualTo(3);
        assertThat(service.findAll())
                .anySatisfy(scenario -> {
                    assertThat(scenario.id()).isEqualTo("SAMPLE-MAIN-001");
                    assertThat(scenario.status()).isEqualTo(SampleScenarioStatus.READY);
                    assertThat(scenario.chain()).contains("来料母单", "出库追溯");
                });
    }
}
