package com.zhubao.erp.register.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 登记入账和设备消费必须有真实账号，不能使用匿名或开发身份绕过岗位服务。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterGatewaySessionAuthorizationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void anonymousFakeBearerAndDevelopmentRoleCannotPostRegisterOrConsumeReading() {
        HttpHeaders fake = new HttpHeaders();
        fake.setBearerAuth("invalid-register-gateway-session");
        HttpHeaders development = new HttpHeaders();
        development.set("X-Zhubao-Role", "ADMIN");
        development.set("X-Zhubao-Operator", "伪造管理员");
        for (HttpHeaders headers : List.of(new HttpHeaders(), fake, development)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            assertForbidden("/api/register-import/batches/NOT-EXIST/posting-commands", Map.of("operator", "伪造操作人"), headers);
            assertForbidden("/api/register-import/batches/confirm", Map.of("operator", "伪造操作人"), headers);
            assertForbidden("/api/register-import/batches/NOT-EXIST/rows/1/corrections", Map.of("fields", Map.of("process", "抛光"), "operator", "伪造操作人"), headers);
            assertForbidden("/api/hardware/gateway/consumptions", Map.of("readingCode", "NOT-EXIST", "intent", "DISMISS"), headers);
        }
    }

    private void assertForbidden(String path, Object body, HttpHeaders headers) {
        var response = restTemplate.postForEntity(path, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).as(path + ": " + response.getBody()).isEqualTo(403);
    }
}
