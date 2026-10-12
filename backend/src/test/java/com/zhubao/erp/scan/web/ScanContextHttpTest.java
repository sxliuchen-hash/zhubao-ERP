package com.zhubao.erp.scan.web;

import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.app.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 上下文查询要求登录；旧解析接口仍只识别格式。 */
@SpringBootTest
@AutoConfigureMockMvc
class ScanContextHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthSessionService sessions;

    @Test
    void contextRequiresASessionAndResolveStaysFormatOnly() throws Exception {
        mvc.perform(post("/api/scan/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"JG|mother|MD-HTTP-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("请先登录后再访问业务数据"));

        mvc.perform(post("/api/scan/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"JG|mother|MD-HTTP-1\",\"scene\":\"lookup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.objectType").value("MOTHER_ORDER"));

        String token = sessions.login(new AuthLoginCommand("admin", "admin123")).token();
        mvc.perform(post("/api/scan/context")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"JG|mother|MD-HTTP-MISSING\",\"intent\":\"LOOKUP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未找到对应单据"));
    }

    @Test
    void progressSaveRequiresLoginAndIgnoresClientIdentity() throws Exception {
        mvc.perform(post("/api/scan/progress-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"MOTHER_ORDER\",\"targetCode\":\"MD-HTTP-MISSING\",\"stage\":\"PRODUCTION\",\"note\":\"补一句\",\"requestId\":\"req-http-01\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("请先登录后再记录过程"));

        String token = sessions.login(new AuthLoginCommand("admin", "admin123")).token();
        mvc.perform(post("/api/scan/progress-records")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"MOTHER_ORDER","targetCode":"MD-HTTP-MISSING","stage":"PRODUCTION","note":"补一句","requestId":"req-http-01","operatorName":"黑客","operatorRole":"ADMIN","quantity":9}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("单据不存在"));
    }
}
