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
        assertThat(service.processTemplates()).hasSize(4);
        assertThat(service.warehouseStates()).hasSize(6);
        assertThat(service.videoParameters()).hasSize(5);
        assertThat(service.changeRequests()).hasSize(3);
        assertThat(service.auditEvents()).hasSize(3);
        assertThat(service.summary().approvalRequiredItems()).isGreaterThanOrEqualTo(6);
        assertThat(service.summary().changeRequests()).isEqualTo(3);
        assertThat(service.summary().auditEvents()).isEqualTo(3);
        assertThat(service.systemParameters())
                .anySatisfy(parameter -> {
                    assertThat(parameter.key()).isEqualTo("inventory.negative.allowed");
                    assertThat(parameter.value()).isEqualTo("false");
                    assertThat(parameter.level()).isEqualTo("C1");
                });
        assertThat(service.rolePermissions())
                .anySatisfy(role -> assertThat(role.sensitiveActions()).contains("报工更正"));
        assertThat(service.processTemplates())
                .anySatisfy(template -> {
                    assertThat(template.code()).isEqualTo("PROC-ENGRAVE-DAY");
                    assertThat(template.snapshotFields()).contains("机器编号", "操机签字");
                });
        assertThat(service.processTemplates())
                .anySatisfy(template -> {
                    assertThat(template.code()).isEqualTo("PROC-POLISH-DAILY");
                    assertThat(template.snapshotFields()).contains("交单审核");
                });
        assertThat(service.videoParameters())
                .anySatisfy(parameter -> assertThat(parameter.fallback()).contains("人工上传"));
        assertThat(service.changeRequests())
                .anySatisfy(request -> assertThat(request.code()).isEqualTo("CFG-CR-20260706-001"));
        assertThat(service.auditEvents())
                .anySatisfy(event -> assertThat(event.traceId()).isEqualTo("TRACE-CFG-002"));
    }
}
