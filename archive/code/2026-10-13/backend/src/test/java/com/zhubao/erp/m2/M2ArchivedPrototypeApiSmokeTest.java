package com.zhubao.erp.m2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** 仅随旧评审原型保存的 API 验证，不参与当前应用编译和测试。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "zhubao.business.workspace.demo-enabled=true")
class M2ArchivedPrototypeApiSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exposesSampleScenarioApis() {
        ResponseEntity<String> scenarios = restTemplate.getForEntity("/api/samples/scenarios", String.class);
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/samples/summary", String.class);

        assertThat(scenarios.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(scenarios.getBody()).contains("SAMPLE-MAIN-001", "客户来料主链路");
        assertThat(summary.getBody()).contains("\"total\":4", "\"ready\":3");
    }

    @Test
    void exposesConfigurationBaselineApis() {
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/configuration/baseline/summary", String.class);
        ResponseEntity<String> parameters = restTemplate.getForEntity("/api/configuration/baseline/system-parameters", String.class);
        ResponseEntity<String> codeRules = restTemplate.getForEntity("/api/configuration/baseline/code-rules", String.class);
        ResponseEntity<String> roles = restTemplate.getForEntity("/api/configuration/baseline/role-permissions", String.class);
        ResponseEntity<String> processes = restTemplate.getForEntity("/api/configuration/baseline/process-templates", String.class);
        ResponseEntity<String> warehouses = restTemplate.getForEntity("/api/configuration/baseline/warehouse-states", String.class);
        ResponseEntity<String> videos = restTemplate.getForEntity("/api/configuration/baseline/video-parameters", String.class);
        ResponseEntity<String> changes = restTemplate.getForEntity("/api/configuration/baseline/change-requests", String.class);
        ResponseEntity<String> audits = restTemplate.getForEntity("/api/configuration/baseline/audit-events", String.class);

        assertThat(summary.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(summary.getBody()).contains("\"systemParameters\":7", "\"warehouseStates\":6", "\"changeRequests\":3");
        assertThat(parameters.getBody()).contains("inventory.negative.allowed", "pay.formal.enabled");
        assertThat(codeRules.getBody()).contains("CC-YYYYMMDD-流水号-客户简称");
        assertThat(roles.getBody()).contains("车间组长", "报工更正");
        assertThat(processes.getBody()).contains("PROC-RING-STD", "PROC-ENGRAVE-DAY", "PROC-POLISH-DAILY", "工资试算");
        assertThat(warehouses.getBody()).contains("RAW-CUSTOMER", "客户所有");
        assertThat(videos.getBody()).contains("video.retry.max", "人工上传");
        assertThat(changes.getBody()).contains("CFG-CR-20260706-001", "待审批");
        assertThat(audits.getBody()).contains("CFG-AUD-20260706-001", "TRACE-CFG-002");
    }
}
