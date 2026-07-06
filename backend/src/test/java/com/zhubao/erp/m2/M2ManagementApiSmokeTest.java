package com.zhubao.erp.m2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class M2ManagementApiSmokeTest {

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
    void exposesHardwareIntegrationApis() {
        ResponseEntity<String> devices = restTemplate.getForEntity("/api/hardware/devices", String.class);
        ResponseEntity<String> tasks = restTemplate.getForEntity("/api/hardware/integration-tasks", String.class);
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/hardware/summary", String.class);

        assertThat(devices.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(devices.getBody()).contains("CAM-UNPACK-01", "SCALE-01");
        assertThat(tasks.getBody()).contains("INT-VID-001", "人工上传");
        assertThat(summary.getBody()).contains("\"totalDevices\":5", "\"fallbackRequired\":4", "\"averageHealth\":70");
    }

    @Test
    void exposesBusinessWorkspaceApis() {
        ResponseEntity<String> inbound = restTemplate.getForEntity("/api/business/workspaces/inbound", String.class);
        ResponseEntity<String> production = restTemplate.getForEntity("/api/business/workspaces/production", String.class);
        ResponseEntity<String> quality = restTemplate.getForEntity("/api/business/workspaces/quality", String.class);
        ResponseEntity<String> inventory = restTemplate.getForEntity("/api/business/workspaces/inventory", String.class);
        ResponseEntity<String> mobile = restTemplate.getForEntity("/api/business/workspaces/mobile", String.class);
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/business/workspaces/summary", String.class);

        assertThat(inbound.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(inbound.getBody()).contains("MD-20260706-004", "拆包视频截取失败");
        assertThat(production.getBody()).contains("HO-20260706-021", "审批阻断");
        assertThat(quality.getBody()).contains("LOSS-20260706-009", "损耗阻断");
        assertThat(inventory.getBody()).contains("LEDGER-20260706-001", "视频证据");
        assertThat(mobile.getBody()).contains("DRAFT-QC-003", "离线草稿");
        assertThat(summary.getBody()).contains("\"inboundOrders\":4", "\"activeWorkOrders\":4", "\"mobileTasks\":3");
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
        assertThat(processes.getBody()).contains("PROC-RING-STD", "工资试算");
        assertThat(warehouses.getBody()).contains("RAW-CUSTOMER", "客户所有");
        assertThat(videos.getBody()).contains("video.retry.max", "人工上传");
        assertThat(changes.getBody()).contains("CFG-CR-20260706-001", "待审批");
        assertThat(audits.getBody()).contains("CFG-AUD-20260706-001", "TRACE-CFG-002");
    }
}
