package com.zhubao.erp.business.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MotherOrderSplitService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 改单接口直接访问时校验客户范围，并向非财务隐藏金额。 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderChangeHttpTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AuthSessionService sessions;
    @Autowired private AuthUserAccountRepository accounts;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private BusinessOperationService operations;
    @Autowired private MotherOrderSplitService splits;
    @Autowired private ProcessStationService stations;

    @Test
    void directHttpHidesMoneyAndRejectsAnotherCustomer() throws Exception {
        String code = "MD-HTTP-" + System.nanoTime();
        operations.confirmInbound(new ConfirmInboundCommand(
                code, 1001L, 1001L, "AU9999", "2026-10-10", "顺丰", "SF" + code, "",
                "库管员 何琳", "库管员 何琳", "戒托", "", null, "", "R05-WIP",
                new BigDecimal("4"), "件", new BigDecimal("8"), "库管员 何琳", "", "",
                "内壁刻字", "2026-10-20", "恒信珠宝"
        ));
        splits.keep(code, "库管员 何琳");
        String other = "MD-HTTP-OTHER-" + System.nanoTime();
        operations.confirmInbound(new ConfirmInboundCommand(
                other, 2002L, 2002L, "AU9999", "2026-10-10", "顺丰", "SF" + other, "",
                "别人", "别人", "戒托", "", null, "", "R05-WIP",
                new BigDecimal("1"), "件", new BigDecimal("2"), "别人", "", "",
                "别人的要求", "2026-10-20", "另一客户"
        ));
        accounts.save(new AuthUserAccount(
                "scope-http", passwordHasher.hash("scope-http123"), "范围员", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE,
                true, false, Instant.now(), 0, null, List.of("1001"), false, false, List.of()
        ));

        mvc.perform(get("/api/business/order-changes/timeline").param("motherOrderCode", code))
                .andExpect(status().isForbidden());

        ObjectNode draft = mapper.createObjectNode();
        draft.put("motherOrderCode", code);
        draft.put("workOrderCode", "");
        draft.put("newRequirement", "改为外圈刻字");
        draft.put("newDueDate", "2026-11-01");
        draft.put("requestId", "http-draft-" + code);
        draft.put("reworkProcess", "");
        String changeId = mvc.perform(post("/api/business/order-changes")
                        .header("Authorization", token("warehouse"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draft)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(changeId).path("data").path("id").asText();

        ObjectNode confirm = mapper.createObjectNode();
        confirm.put("amountUnchanged", false);
        confirm.put("customerUnitPrice", 70);
        confirm.put("workerFee", 8);
        confirm.put("customerDelta", 0);
        confirm.put("workerDelta", 0);
        confirm.put("note", "看金额");
        mvc.perform(post("/api/business/order-changes/" + id + "/finance-confirmations")
                        .header("Authorization", token("warehouse"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(confirm)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/business/order-changes/" + id + "/finance-submissions")
                        .header("Authorization", token("warehouse")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
        ObjectNode applyEarly = mapper.createObjectNode();
        applyEarly.put("requestId", "http-apply-early-" + code);
        mvc.perform(post("/api/business/order-changes/" + id + "/applications")
                        .header("Authorization", token("warehouse"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(applyEarly)))
                .andExpect(status().isBadRequest());
        ObjectNode review = mapper.createObjectNode();
        review.put("approved", true);
        review.put("note", "要求路线交期通过");
        mvc.perform(post("/api/business/order-changes/" + id + "/reviews")
                        .header("Authorization", token("warehouse"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(review)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/business/order-changes/" + id + "/reviews")
                        .header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(review)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BUSINESS_APPROVED"));
        mvc.perform(post("/api/business/order-changes/" + id + "/finance-confirmations")
                        .header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(confirm)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/business/order-changes/" + id).header("Authorization", token("warehouse")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerUnitPrice").value(0))
                .andExpect(jsonPath("$.data.newRequirement").value("改为外圈刻字"));
        mvc.perform(get("/api/business/order-changes/timeline").param("motherOrderCode", code)
                        .header("Authorization", token("warehouse")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1]", not(containsString("客户单价"))));
        mvc.perform(get("/api/business/order-changes/timeline").param("motherOrderCode", code)
                        .header("Authorization", token("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1]", not(containsString("客户单价:70"))));

        mvc.perform(get("/api/business/order-changes/timeline").param("motherOrderCode", other)
                        .header("Authorization", token("scope-http")))
                .andExpect(status().isForbidden());

        ObjectNode apply = mapper.createObjectNode();
        apply.put("requestId", "http-apply-" + code);
        apply.put("expectedVersion", 0);
        mvc.perform(post("/api/business/order-changes/" + id + "/applications")
                        .header("Authorization", token("warehouse"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(apply)))
                .andExpect(status().isOk());
        AuthUserAccount admin = accounts.findByUsername("admin").orElseThrow();
        assertThat(stations.lookup(code, admin).processingRequirement()).isEqualTo("改为外圈刻字");
    }

    private String token(String username) {
        String password = "admin".equals(username) ? "admin123" : username + "123";
        return "Bearer " + sessions.login(new AuthLoginCommand(username, password)).token();
    }
}
