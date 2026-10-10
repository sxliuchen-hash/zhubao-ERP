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
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 兼容业务 HTTP 接口必须与现场执行相同的会话、工序与质检规则。 */
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
    void genericProductionEnforcesBindingSequenceAndRecordsCompleteHandoffMetadata() throws Exception {
        String code = keptMother();
        postAs("polish", "/production-reports", production(code, "抛光", "4"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("还没交")));
        postAs("operator", "/production-reports", production(code, "雕刻", "4"))
                .andExpect(status().isBadRequest());
        ObjectNode command = mapper.valueToTree(production(code, "雕刻", "4"));
        command.remove("operator");
        postAs("carve", "/production-reports", command)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.toStockStatus").value("WIP"))
                .andExpect(jsonPath("$.data.operator").value("雕刻工 林海"));
        assertThat(documents.findProcessStationHandoff(code, "雕刻").orElseThrow().operator()).isEqualTo("雕刻工 林海");
        assertThat(stocks.ledgers()).filteredOn(entry -> code.equals(entry.documentCode()) && "process_station_report".equals(entry.documentType()))
                .singleElement().satisfies(entry -> assertThat(entry.remark())
                        .contains("机器编号：R05-MACHINE", "工作项目：戒托", "来源登记表：原始登记", "原表行号：7", "纸质序号：A-7", "备注：保留现场备注", "操机签字：原表签字")
                        .doesNotContain("冒名操作人"));
        assertThat(quantity(code, StockStatus.WIP)).isEqualByComparingTo("4");
        assertThat(quantity(code, StockStatus.SEMI_FINISHED)).isZero();
        postAs("carve", "/production-reports", production(code, "雕刻", "4")).andExpect(status().isBadRequest());
        postAs("inlay", "/production-reports", production(code, "镶嵌", "4")).andExpect(status().isOk());
        postAs("polish", "/production-reports", production(code, "抛光", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.toStockStatus").value("SEMI_FINISHED"));
        assertThat(documents.listProcessStationHandoffs(code)).hasSize(3);
    }

    @Test
    void genericProductionRejectsPartialUnreviewedAndForgedFormalDimensions() throws Exception {
        String code = keptMother();
        postAs("carve", "/production-reports", production(code, "雕刻", "2")).andExpect(status().isBadRequest());
        ObjectNode unreviewed = mapper.valueToTree(production(code, "雕刻", "4"));
        unreviewed.put("handoffAuditStatus", "待审核");
        postAs("carve", "/production-reports", unreviewed).andExpect(status().isBadRequest());
        ObjectNode forged = mapper.valueToTree(production(code, "雕刻", "4"));
        forged.put("customerId", 1002);
        postAs("carve", "/production-reports", forged).andExpect(status().isBadRequest());
        assertThat(documents.listProcessStationHandoffs(code)).isEmpty();
        assertThat(quantity(code, StockStatus.RAW_WAITING)).isEqualByComparingTo("4");
    }

    @Test
    void genericReleaseRequiresLastStationAndSavedPassingCheckAndUsesSessionInspector() throws Exception {
        String code = keptMother();
        postAs("qc", "/quality-releases", release(code)).andExpect(status().isBadRequest());
        finish(code);
        postAs("qc", "/quality-releases", release(code)).andExpect(status().isBadRequest());
        postAs("qc", "/quality-checks", new QualityCheckCommand(code, "冒名质检员", List.of(
                new QualityCheckLineCommand(1, QualityVerdicts.PASS, ""))))
                .andExpect(status().isOk());
        ObjectNode body = mapper.valueToTree(release(code));
        body.remove("inspector");
        postAs("qc", "/quality-releases", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspector").value("质检员 周娜"))
                .andExpect(jsonPath("$.data.releaseResult").value("复检合格，允许成品入库"));
        assertThat(quantity(code, StockStatus.FINISHED)).isEqualByComparingTo("4");

        String failed = keptMother();
        finish(failed);
        postAs("qc", "/quality-checks", new QualityCheckCommand(failed, null, List.of(
                new QualityCheckLineCommand(1, QualityVerdicts.FAIL, "内壁漏刻"))))
                .andExpect(status().isOk());
        postAs("qc", "/quality-releases", release(failed)).andExpect(status().isBadRequest());
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

    private ReleaseQualityStockCommand release(String code) {
        return new ReleaseQualityStockCommand("QC-" + code, code, code, 1001L, 1001L, "AU9999", WAREHOUSE, "R05-FINISHED",
                bd("4"), bd("8"), "抛光", "冒名质检员", "复检合格，允许成品入库", "EV-R05", "放行说明");
    }

    private BigDecimal quantity(String motherCode, StockStatus status) {
        return stocks.balances().stream().filter(balance -> motherCode.equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == status).map(StockBalance::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nextCode() { return "MD-R05-" + System.nanoTime() + "-" + SEQUENCE.incrementAndGet(); }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
