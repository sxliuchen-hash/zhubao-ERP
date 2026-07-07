package com.zhubao.erp.business.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptanceFixtureContractTest {

    private static final String FIXTURE = "acceptance/real-flow-scenario-20260706.json";
    private static final List<String> REQUIRED_COMMAND_SECTIONS = List.of(
            "productionReport",
            "qualityFreeze",
            "reworkDispatch",
            "reworkReport",
            "qualityRelease",
            "outboundShipment",
            "customerReturn",
            "afterSalesReview",
            "lossApproval"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void realFlowFixtureKeepsBusinessChainEvidenceAndExpectedBalancesTogether() throws IOException {
        JsonNode scenario = loadScenario();

        assertThat(scenario.path("scenarioCode").asText()).isEqualTo("REAL-FLOW-20260706-001");
        assertThat(scenario.path("sourceNote").asText()).contains("脱敏验收样例");
        assertThat(scenario.path("motherOrderCode").asText()).startsWith("MD-");
        assertThat(scenario.path("workOrderCode").asText()).startsWith("ZD-");
        assertThat(scenario.path("materialCode").asText()).isNotBlank();

        for (String section : REQUIRED_COMMAND_SECTIONS) {
            assertThat(scenario.path(section).isObject()).as(section + " section exists").isTrue();
            assertThat(scenario.path(section).path("remark").asText()).as(section + " keeps acceptance remark").contains("脱敏样例");
        }

        assertEvidenceCode(scenario, "qualityFreeze", "PHOTO-QC-");
        assertEvidenceCode(scenario, "reworkDispatch", "PHOTO-QC-");
        assertEvidenceCode(scenario, "reworkReport", "PHOTO-REWORK-");
        assertEvidenceCode(scenario, "qualityRelease", "PHOTO-RELEASE-");
        assertEvidenceCode(scenario, "outboundShipment", "ATT-OUT-");
        assertEvidenceCode(scenario, "customerReturn", "ATT-RET-");
        assertEvidenceCode(scenario, "afterSalesReview", "PHOTO-ASR-");
        assertEvidenceCode(scenario, "lossApproval", "HO-");

        JsonNode expectedLedgerTypes = scenario.path("expectedLedgerTypes");
        assertThat(expectedLedgerTypes.isArray()).isTrue();
        assertThat(expectedLedgerTypes.size()).isEqualTo(REQUIRED_COMMAND_SECTIONS.size());
        assertThat(textArray(expectedLedgerTypes))
                .contains("production_report", "quality_freeze", "after_sales_review");
        assertThat(scenario.path("expectedBalances").isArray()).isTrue();
        assertThat(scenario.path("expectedBalances").size()).isGreaterThanOrEqualTo(6);
        assertThat(scenario.path("expectedLossApproval").path("formalPayroll").asBoolean()).isFalse();
    }

    private JsonNode loadScenario() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(FIXTURE)) {
            assertThat(inputStream).as("acceptance fixture exists: %s", FIXTURE).isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }

    private void assertEvidenceCode(JsonNode scenario, String section, String prefix) {
        assertThat(scenario.path(section).path("evidenceCode").asText())
                .as(section + " evidence code")
                .startsWith(prefix);
    }

    private List<String> textArray(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText()));
        return values;
    }
}
