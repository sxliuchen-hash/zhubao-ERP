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
}
