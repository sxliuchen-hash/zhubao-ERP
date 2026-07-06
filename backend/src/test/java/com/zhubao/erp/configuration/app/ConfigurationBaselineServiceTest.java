package com.zhubao.erp.configuration.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationBaselineServiceTest {

    @Test
    void exposesConfigurationBaselineForReviewAndImplementation() {
        ConfigurationBaselineService service = new ConfigurationBaselineService();

        assertThat(service.systemParameters()).hasSize(7);
        assertThat(service.codeRules()).hasSize(5);
        assertThat(service.rolePermissions()).hasSize(6);
        assertThat(service.processTemplates()).hasSize(2);
        assertThat(service.warehouseStates()).hasSize(6);
        assertThat(service.videoParameters()).hasSize(5);
        assertThat(service.summary().approvalRequiredItems()).isGreaterThanOrEqualTo(6);
        assertThat(service.systemParameters())
                .anySatisfy(parameter -> {
                    assertThat(parameter.key()).isEqualTo("inventory.negative.allowed");
                    assertThat(parameter.value()).isEqualTo("false");
                    assertThat(parameter.level()).isEqualTo("C1");
                });
        assertThat(service.rolePermissions())
                .anySatisfy(role -> assertThat(role.sensitiveActions()).contains("报工更正"));
        assertThat(service.videoParameters())
                .anySatisfy(parameter -> assertThat(parameter.fallback()).contains("人工上传"));
    }
}
