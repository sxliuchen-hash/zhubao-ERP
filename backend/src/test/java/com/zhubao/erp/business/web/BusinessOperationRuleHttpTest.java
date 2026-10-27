package com.zhubao.erp.business.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MotherOrderSplitService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.app.QualityRequirementService;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 现场工序交付和质检放行的 HTTP 入口必须执行会话、工序与轮次规则。 */
@SpringBootTest
@AutoConfigureMockMvc
class BusinessOperationRuleHttpTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final String ROOT = "/api/business/operations";
    private static final String WAREHOUSE = "R05-WIP";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AuthSessionService sessions;
    @Autowired private BusinessOperationService operations;
    @Autowired private MotherOrderSplitService splits;
    @Autowired private BusinessDocumentRepository documents;
    @Autowired private StockLedgerService stocks;
    @Autowired private ProcessStationService stations;
    @Autowired private QualityRequirementService quality;
    @Autowired private AuthUserAccountRepository accounts;
    private final java.util.Map<String, AuthUserAccount> originalAccounts = new java.util.HashMap<>();

    @BeforeEach
    void prepareProcessRuleAccounts() {
        // 这些场景验证工序顺序，明确赋予测试数据范围，避免被默认本人范围提前拦截。
        for (String username : List.of("operator", "carve", "inlay", "polish", "qc")) {
            AuthUserAccount account = accounts.findByUsername(username).orElseThrow();
            originalAccounts.put(username, account);
            accounts.save(new AuthUserAccount(account.username(), account.passwordHash(), account.operator(), account.role(),
                    DataScope.ALL, account.enabled(), account.demoAccount(), account.passwordChangedAt(), account.failedLoginCount(),
                    account.lockedUntil(), account.customerScope(), account.passwordChangeRequired(), account.temporaryPasswordConsumed(),
                    account.allowedProcesses(), account.coverDuties(), account.teamCode(), account.departmentCode()));
        }
    }

    @AfterEach
    void restoreProcessRuleAccounts() {
        originalAccounts.values().forEach(accounts::save);
    }

    @Test
    void 管理员可更正母单客户删除恢复并查看操作日志且其他角色被拒绝() throws Exception {
        String code = keptMother();
        String admin = "Bearer " + sessions.login(new AuthLoginCommand("admin", "admin123")).token();
        String worker = "Bearer " + sessions.login(new AuthLoginCommand("carve", "carve123")).token();
        String root = "/api/business/order-maintenance";
        String response = mvc.perform(get(root).param("code", code).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String revision = mapper.readTree(response).path("data").get(0).path("revision").asText();
        var body = new com.zhubao.erp.business.app.MotherOrderAdminService.BasicInfo(1002L, "更正货品", "更正备注",
                "误选客户", "req-basic-http-" + code, revision);
        mvc.perform(post(root + "/mother-orders/" + code + "/basic-info").header("Authorization", worker)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isForbidden());
        mvc.perform(post(root + "/mother-orders/" + code + "/basic-info").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.customerId").value(1002));
        assertThat(documents.findMotherOrderByCode(code).orElseThrow().ownerCustomerId()).isEqualTo(1002L);
        assertThat(stocks.balances().stream().filter(row -> code.equals(row.dimension().motherOrderCode())
                && row.dimension().customerId().equals(1001L)).map(StockBalance::quantity).reduce(BigDecimal.ZERO, BigDecimal::add)).isZero();
        mvc.perform(post(root + "/recycle").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new com.zhubao.erp.business.app.OrderMaintenanceCommands.RecycleCommand(
                                "MOTHER", code, "重复录单", "req-delete-http-" + code))))
                .andExpect(status().isOk());
        assertThat(documents.findMotherOrderByCode(code)).isEmpty();
        mvc.perform(post(root + "/restore").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new com.zhubao.erp.business.app.OrderMaintenanceCommands.RestoreCommand(
                                "MOTHER", code, "req-restore-http-" + code))))
                .andExpect(status().isOk());
        mvc.perform(get(root + "/operation-logs").param("code", code).header("Authorization", worker)).andExpect(status().isForbidden());
        mvc.perform(get(root + "/operation-logs").param("code", code)).andExpect(status().isForbidden());
        mvc.perform(get(root + "/operation-logs").param("code", code).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.action == '管理员维护')]").isArray())
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void 业务管理保留顶岗与多工种授权并能登记进度和交工() throws Exception {
        AuthUserAccount seed = accounts.findByUsername("carve").orElseThrow();
        String username = "manager-report-" + SEQUENCE.incrementAndGet();
        accounts.save(new AuthUserAccount(username, seed.passwordHash(), "顶岗师傅", RoleCode.BUSINESS_MANAGER,
                DataScope.ALL, true, true, seed.passwordChangedAt(), 0, null, List.of(), false, false,
                List.of("雕刻", "镶嵌"), List.of("REPORT"), "一组", "生产部"));
        String token = sessions.login(new AuthLoginCommand(username, "carve123")).token();
        String code = keptMother();
        mvc.perform(post(ROOT + "/process-progress").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(
                                new ProcessProgressController.ProcessProgressRequest(code, 30, 0, "开始加工", "", "req-progress-" + code))))
                .andExpect(status().isOk());
        mvc.perform(post(ROOT + "/process-stations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(new ProcessStationReportCommand(code, "雕刻", "req-manager-carve-" + code))))
                .andExpect(status().isOk());
        mvc.perform(post(ROOT + "/process-stations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(new ProcessStationReportCommand(code, "镶嵌", "req-manager-inlay-" + code))))
                .andExpect(status().isOk());
        assertThat(documents.listProcessStationHandoffs(code)).hasSize(2)
                .allMatch(row -> row.operator().equals("顶岗师傅"));
        mvc.perform(post(ROOT + "/process-stations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(new ProcessStationReportCommand(code, "抛光", "req-manager-polish-" + code))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 业务管理只有工种没有报工顶岗权限仍不能提交() throws Exception {
        AuthUserAccount seed = accounts.findByUsername("carve").orElseThrow();
        String username = "manager-intake-" + SEQUENCE.incrementAndGet();
        accounts.save(new AuthUserAccount(username, seed.passwordHash(), "收货顶岗", RoleCode.BUSINESS_MANAGER,
                DataScope.ALL, true, true, seed.passwordChangedAt(), 0, null, List.of(), false, false,
                List.of("雕刻"), List.of("INTAKE")));
        String token = sessions.login(new AuthLoginCommand(username, "carve123")).token();
        String code = keptMother();
        mvc.perform(post(ROOT + "/process-stations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(new ProcessStationReportCommand(code, "雕刻", "req-no-report-" + code))))
                .andExpect(status().isForbidden());
        assertThat(documents.listProcessStationHandoffs(code)).isEmpty();
    }

    @Test
    void anonymousHeadersCannotCreateInboundAndSessionIdentityReplacesMissingOrForgedActor() throws Exception {
        String code = nextCode();
        ObjectNode body = mapper.valueToTree(inbound(code));
        body.remove("operator");
        mvc.perform(post(ROOT + "/inbound-confirmations").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Zhubao-Operator", "冒名操作人").header("X-Zhubao-Role", "ADMIN")
                        .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isForbidden());
        assertThat(documents.findMotherOrderByCode(code)).isEmpty();

        postAs("admin", "/inbound-confirmations", body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.operator").value("系统管理员"));
        assertThat(documents.findMotherOrderByCode(code).orElseThrow().operator()).isEqualTo("系统管理员");

        String forgedCode = nextCode();
        ObjectNode forged = mapper.valueToTree(inbound(forgedCode));
        forged.put("operator", "冒名操作人");
        postAs("admin", "/inbound-confirmations", forged).andExpect(status().isOk());
        assertThat(documents.findMotherOrderByCode(forgedCode).orElseThrow().operator()).isEqualTo("系统管理员");
    }

    @Test
    void stationHttpEnforcesBindingSequenceAndSessionOperator() throws Exception {
        String code = keptMother();
        postAs("polish", "/process-stations", new ProcessStationReportCommand(code, "抛光", "req-early-" + code))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("还没交")));
        postAs("operator", "/process-stations", new ProcessStationReportCommand(code, "雕刻", "req-op-" + code))
                .andExpect(status().isBadRequest());
        postAs("carve", "/process-stations", new ProcessStationReportCommand(code, "雕刻", "req-carve-" + code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stockStatus").value("WIP"));
        assertThat(documents.findProcessStationHandoff(code, "雕刻").orElseThrow().operator()).isEqualTo("雕刻工 林海");
        assertThat(quantity(code, StockStatus.WIP)).isEqualByComparingTo("4");
        assertThat(quantity(code, StockStatus.SEMI_FINISHED)).isZero();
        postAs("carve", "/process-stations", new ProcessStationReportCommand(code, "雕刻", "req-carve-again-" + code))
                .andExpect(status().isBadRequest());
        postAs("inlay", "/process-stations", new ProcessStationReportCommand(code, "镶嵌", "req-inlay-" + code)).andExpect(status().isOk());
        postAs("polish", "/process-stations", new ProcessStationReportCommand(code, "抛光", "req-polish-" + code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stockStatus").value("SEMI_FINISHED"));
        assertThat(documents.listProcessStationHandoffs(code)).hasSize(3);
    }

    @Test
    void registerStyleReportRejectsPartialUnreviewedAndForgedFormalDimensions() {
        String code = keptMother();
        AuthUserAccount carve = accounts.findByUsername("carve").orElseThrow();
        assertThatThrownBy(() -> stations.reportProduction(production(code, "雕刻", "2"), carve))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stations.reportProduction(unreviewed(code), carve))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stations.reportProduction(forged(code), carve))
                .isInstanceOf(BusinessException.class);
        assertThat(documents.listProcessStationHandoffs(code)).isEmpty();
        assertThat(quantity(code, StockStatus.RAW_WAITING)).isEqualByComparingTo("4");
    }

    @Test
    void qualityReleaseRequiresLastStationAndSavedPassingCheckAndUsesSessionInspector() throws Exception {
        String code = keptMother();
        postAs("qc", "/quality-checks/releases", new QualityFollowCommand(code, "req-release-early-" + code))
                .andExpect(status().isBadRequest());
        finish(code);
        postAs("qc", "/quality-checks/releases", new QualityFollowCommand(code, "req-release-nocheck-" + code))
                .andExpect(status().isBadRequest());
        postAs("qc", "/quality-checks", new QualityCheckCommand(code, "冒名质检员", List.of(
                new QualityCheckLineCommand(1, QualityVerdicts.PASS, ""))))
                .andExpect(status().isOk());
        postAs("qc", "/quality-checks/releases", new QualityFollowCommand(code, "req-release-" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspector").value("质检员 周娜"))
                .andExpect(jsonPath("$.data.toStockStatus").value("FINISHED"));
        assertThat(quantity(code, StockStatus.FINISHED)).isEqualByComparingTo("4");

        String failed = keptMother();
        finish(failed);
        postAs("qc", "/quality-checks", new QualityCheckCommand(failed, null, List.of(
                new QualityCheckLineCommand(1, QualityVerdicts.FAIL, "内壁漏刻"))))
                .andExpect(status().isOk());
        postAs("qc", "/quality-checks/releases", new QualityFollowCommand(failed, "req-release-fail-" + failed))
                .andExpect(status().isBadRequest());
        assertThat(quantity(failed, StockStatus.FINISHED)).isZero();
    }

    @Test
    void stationAndQualityRejectCustomersOutsideExplicitScopeBeforeFirstAssociation() {
        String code = keptMother();
        AuthUserAccount outsideWorker = new AuthUserAccount("outside-worker", "hash", "受限工人", RoleCode.OPERATOR,
                DataScope.SELF, true, false, List.of("1002")).withAllowedProcesses(List.of("雕刻"));
        AuthUserAccount outsideInspector = new AuthUserAccount("outside-qc", "hash", "受限质检", RoleCode.QUALITY_INSPECTOR,
                DataScope.DEPARTMENT, true, false, List.of("1002"));
        assertThatThrownBy(() -> stations.lookup(code, outsideWorker)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> stations.reportProduction(production(code, "雕刻", "4"), outsideWorker)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> quality.lookup(code, outsideInspector)).isInstanceOf(PermissionDeniedException.class);
        assertThat(documents.listProcessStationHandoffs(code)).isEmpty();
    }

    private ResultActions postAs(String username, String path, Object command) throws Exception {
        String token = sessions.login(new AuthLoginCommand(username, username + "123")).token();
        return mvc.perform(post(ROOT + path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)));
    }

    private void finish(String code) throws Exception {
        postAs("carve", "/process-stations", new ProcessStationReportCommand(code, "雕刻")).andExpect(status().isOk());
        postAs("inlay", "/process-stations", new ProcessStationReportCommand(code, "镶嵌")).andExpect(status().isOk());
        postAs("polish", "/process-stations", new ProcessStationReportCommand(code, "抛光")).andExpect(status().isOk());
    }

    private String keptMother() {
        String code = nextCode();
        operations.confirmInbound(inbound(code));
        splits.keep(code, "测试收货人");
        return code;
    }

    private ConfirmInboundCommand inbound(String code) {
        return new ConfirmInboundCommand(code, 1001L, 1001L, "AU9999", "2026-10-10", "顺丰", "SF" + code.replace("-", ""),
                "", "测试收货人", "测试收货人", "戒托", "", null, "", WAREHOUSE,
                bd("4"), "件", bd("8"), "测试收货人", "", "", "内壁刻字", "2026-10-20", "恒信珠宝");
    }

    private ReportProductionCommand production(String code, String process, String quantity) {
        return new ReportProductionCommand(code, code, 1001L, 1001L, "AU9999", WAREHOUSE, WAREHOUSE,
                bd(quantity), "件", bd("8"), "2026-10-10", process, "加工", "戒托", "R05-MACHINE", "戒托",
                "08:00", "09:00", "已完成", "已审核", "原始登记", 7, "A-7", "冒名操作人", "原表签字", "HO-R05", "保留现场备注");
    }

    private ReportProductionCommand unreviewed(String code) {
        return new ReportProductionCommand(code, code, 1001L, 1001L, "AU9999", WAREHOUSE, WAREHOUSE,
                bd("4"), "件", bd("8"), "2026-10-10", "雕刻", "加工", "戒托", "R05-MACHINE", "戒托",
                "08:00", "09:00", "已完成", "待审核", "原始登记", 7, "A-7", "冒名操作人", "原表签字", "HO-R05", "保留现场备注");
    }

    private ReportProductionCommand forged(String code) {
        return new ReportProductionCommand(code, code, 1002L, 1002L, "AU9999", WAREHOUSE, WAREHOUSE,
                bd("4"), "件", bd("8"), "2026-10-10", "雕刻", "加工", "戒托", "R05-MACHINE", "戒托",
                "08:00", "09:00", "已完成", "已审核", "原始登记", 7, "A-7", "冒名操作人", "原表签字", "HO-R05", "保留现场备注");
    }

    private BigDecimal quantity(String motherCode, StockStatus status) {
        return stocks.balances().stream().filter(balance -> motherCode.equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == status).map(StockBalance::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nextCode() { return "MD-R05-" + System.nanoTime() + "-" + SEQUENCE.incrementAndGet(); }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
