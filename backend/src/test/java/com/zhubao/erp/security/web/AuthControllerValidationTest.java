package com.zhubao.erp.security.web;

import com.zhubao.erp.common.error.GlobalExceptionHandler;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.NavigationMenuService;
import com.zhubao.erp.security.app.PasswordExpiryPolicy;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.app.PasswordPolicy;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerValidationTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PasswordHasher hasher = new PasswordHasher();
        AuthSessionService sessions = new AuthSessionService(
                new NavigationMenuService(new InMemoryNavigationMenuRepository()),
                new InMemoryAuthAuditRepository(), new InMemoryAuthUserAccountRepository(hasher),
                hasher, new PasswordExpiryPolicy(), new PasswordPolicy());
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(sessions, null, null, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validLoginStillCreatesSession() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"leader\",\"password\":\"leader123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("leader"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"username":"private-user","password":""} | 请输入密码
            {"username":"private-user","password":"   "} | 请输入密码
            {"username":"private-user"} | 请输入密码
            {"username":"","password":"private-password"} | 请输入用户名
            {"password":"private-password"} | 请输入用户名
            {} | 请输入密码；请输入用户名
            """)
    void invalidLoginReturnsSafeChineseMessage(String body, String message) throws Exception {
        assertValidationResponse("/api/auth/login", body, message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"currentPassword":"","newPassword":"private-password"} | 请输入当前密码
            {"newPassword":"private-password"} | 请输入当前密码
            {"currentPassword":"private-password","newPassword":""} | 请输入新密码
            {"currentPassword":"private-password"} | 请输入新密码
            {} | 请输入当前密码；请输入新密码
            """)
    void invalidPasswordChangeReturnsSafeChineseMessage(String body, String message) throws Exception {
        assertValidationResponse("/api/auth/password", body, message);
    }

    private void assertValidationResponse(String path, String body, String message) throws Exception {
        String response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(422))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response).doesNotContain("private-user", "private-password", "rejected value",
                "AuthController", "MethodArgumentNotValidException", "login(", "changeOwnPassword(");
    }
}
