package com.zhubao.erp.m2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

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
        ResponseEntity<String> pocRecords = restTemplate.getForEntity("/api/hardware/poc-records", String.class);
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/hardware/summary", String.class);

        assertThat(devices.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(devices.getBody()).contains("CAM-UNPACK-01", "SCALE-01");
        assertThat(tasks.getBody()).contains("INT-VID-001", "人工上传");
        assertThat(pocRecords.getBody()).contains("POC-SCAN-20260706-001", "弱光扫码");
        assertThat(summary.getBody()).contains(
                "\"totalDevices\":5",
                "\"fallbackRequired\":4",
                "\"averageHealth\":70",
                "\"pocRecords\":4",
                "\"pocBlocked\":1"
        );
    }

    @Test
    void exposesDocumentManagementApis() {
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/documents/summary", String.class);
        ResponseEntity<String> packages = restTemplate.getForEntity("/api/documents/packages", String.class);
        ResponseEntity<String> attachments = restTemplate.getForEntity("/api/documents/attachments", String.class);
        ResponseEntity<String> auditEvents = restTemplate.getForEntity("/api/documents/audit-events", String.class);

        assertThat(summary.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(summary.getBody()).contains(
                "\"packages\":4",
                "\"missingEvidence\":3",
                "\"manualFallbacks\":3",
                "\"attachments\"",
                "\"auditEvents\""
        );
        assertThat(packages.getBody()).contains("DOC-QC-20260706-021", "质检返工证据包");
        assertThat(attachments.getBody()).contains("ATT-QC-20260706-021", "QC-20260706-021-defects.zip");
        assertThat(auditEvents.getBody()).contains("DOC-AUD-004", "配置变更单等待审批");
    }

    @Test
    void uploadsDocumentAttachmentAndReadsPreviewContent() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("documentCode", "DOC-INBOUND-20260706-001");
        body.add("businessType", "称重交接");
        body.add("businessCode", "HO-SMOKE-20260706-001");
        body.add("source", "API smoke 上传");
        body.add("owner", "库管员 何琳");
        body.add("fallbackAction", "无");
        body.add("file", new ByteArrayResource("smoke upload content".getBytes()) {
            @Override
            public String getFilename() {
                return "smoke-weight.txt";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> uploaded = restTemplate.postForEntity(
                "/api/documents/attachments/uploads",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(uploaded.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(uploaded.getBody()).contains("ATT-UP-", "smoke-weight.txt", "sha256:", "preview-demo");

        String uploadedCode = uploaded.getBody().replaceAll(".*\"code\":\"([^\"]+)\".*", "$1");
        ResponseEntity<String> preview = restTemplate.getForEntity(
                "/api/documents/attachments/" + uploadedCode + "/preview-url",
                String.class
        );
        ResponseEntity<byte[]> content = restTemplate.getForEntity(
                "/api/documents/attachments/" + uploadedCode + "/content?token=preview-demo",
                byte[].class
        );

        assertThat(preview.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(preview.getBody()).contains(uploadedCode, "/content?token=preview-demo");
        assertThat(content.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new String(content.getBody())).isEqualTo("smoke upload content");
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
    void confirmsInboundThroughBusinessOperationApiAndWritesInventoryLedger() {
        Map<String, Object> request = Map.of(
                "motherOrderCode", "MD-SMOKE-20260706-001",
                "customerId", 1001L,
                "materialCode", "MAT-18K-RING",
                "warehouseCode", "RAW-CUSTOMER",
                "quantity", new BigDecimal("12"),
                "weight", new BigDecimal("18.500"),
                "operator", "库管员 何琳",
                "evidenceCode", "SF-SMOKE-001",
                "remark", "smoke test"
        );

        ResponseEntity<String> confirmed = restTemplate.postForEntity(
                "/api/business/operations/inbound-confirmations",
                request,
                String.class
        );
        ResponseEntity<String> balances = restTemplate.getForEntity("/api/inventory/balances", String.class);

        assertThat(confirmed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(confirmed.getBody()).contains(
                "MD-SMOKE-20260706-001",
                "RAW-CUSTOMER",
                "RAW_WAITING",
                "SF-SMOKE-001"
        );
        assertThat(balances.getBody()).contains(
                "MD-SMOKE-20260706-001",
                "MAT-18K-RING",
                "RAW-CUSTOMER",
                "RAW_WAITING"
        );
    }

    @Test
    void reportsProductionThroughBusinessOperationApiAndMovesWipStock() {
        Map<String, Object> wipDimension = Map.of(
                "ownerType", "CUSTOMER",
                "ownerCustomerId", 1002L,
                "customerId", 1002L,
                "motherOrderCode", "MD-SMOKE-20260706-002",
                "workOrderCode", "ZD-SMOKE-20260706-006",
                "materialCode", "MAT-SILVER-CHAIN",
                "warehouseCode", "WIP-LINE-01",
                "stockStatus", "WIP"
        );
        Map<String, Object> seedWip = Map.of(
                "documentType", "split_work_order",
                "documentCode", "ZD-SMOKE-20260706-006",
                "to", wipDimension,
                "quantity", new BigDecimal("300"),
                "weight", new BigDecimal("683.200"),
                "remark", "seed WIP for production report smoke test"
        );
        restTemplate.postForEntity("/api/inventory/ledger/transfers", seedWip, String.class);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("motherOrderCode", "MD-SMOKE-20260706-002");
        request.put("workOrderCode", "ZD-SMOKE-20260706-006");
        request.put("customerId", 1002L);
        request.put("materialCode", "MAT-SILVER-CHAIN");
        request.put("fromWarehouseCode", "WIP-LINE-01");
        request.put("toWarehouseCode", "SEMI-FINISHED");
        request.put("quantity", new BigDecimal("210"));
        request.put("weight", new BigDecimal("478.240"));
        request.put("process", "焊接");
        request.put("operator", "王强");
        request.put("handoffCode", "HO-SMOKE-001");
        request.put("remark", "production report smoke test");

        ResponseEntity<String> reported = restTemplate.postForEntity(
                "/api/business/operations/production-reports",
                request,
                String.class
        );
        ResponseEntity<String> balances = restTemplate.getForEntity("/api/inventory/balances", String.class);

        assertThat(reported.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(reported.getBody()).contains(
                "ZD-SMOKE-20260706-006",
                "WIP",
                "SEMI_FINISHED",
                "HO-SMOKE-001"
        );
        assertThat(balances.getBody()).contains(
                "MD-SMOKE-20260706-002",
                "ZD-SMOKE-20260706-006",
                "SEMI-FINISHED",
                "SEMI_FINISHED"
        );
    }

    @Test
    void freezesQualityStockThroughBusinessOperationApiAndMovesSemiFinishedStock() {
        Map<String, Object> semiFinishedDimension = Map.of(
                "ownerType", "CUSTOMER",
                "ownerCustomerId", 1002L,
                "customerId", 1002L,
                "motherOrderCode", "MD-SMOKE-20260706-003",
                "workOrderCode", "ZD-SMOKE-20260706-022",
                "materialCode", "MAT-SILVER-CHAIN",
                "warehouseCode", "SEMI-FINISHED",
                "stockStatus", "SEMI_FINISHED"
        );
        Map<String, Object> seedSemiFinished = Map.of(
                "documentType", "production_report",
                "documentCode", "ZD-SMOKE-20260706-022",
                "to", semiFinishedDimension,
                "quantity", new BigDecimal("80"),
                "weight", new BigDecimal("121.150"),
                "remark", "seed semi-finished for quality freeze smoke test"
        );
        restTemplate.postForEntity("/api/inventory/ledger/transfers", seedSemiFinished, String.class);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("qualityCaseCode", "QC-SMOKE-20260706-022");
        request.put("motherOrderCode", "MD-SMOKE-20260706-003");
        request.put("workOrderCode", "ZD-SMOKE-20260706-022");
        request.put("customerId", 1002L);
        request.put("materialCode", "MAT-SILVER-CHAIN");
        request.put("fromWarehouseCode", "SEMI-FINISHED");
        request.put("toWarehouseCode", "FROZEN-QC");
        request.put("quantity", new BigDecimal("6"));
        request.put("weight", new BigDecimal("13.660"));
        request.put("process", "焊接");
        request.put("inspector", "周娜");
        request.put("defect", "焊点偏移 6 件");
        request.put("evidenceCode", "PHOTO-QC-SMOKE-001");
        request.put("remark", "quality freeze smoke test");

        ResponseEntity<String> frozen = restTemplate.postForEntity(
                "/api/business/operations/quality-freezes",
                request,
                String.class
        );
        ResponseEntity<String> balances = restTemplate.getForEntity("/api/inventory/balances", String.class);

        assertThat(frozen.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(frozen.getBody()).contains(
                "QC-SMOKE-20260706-022",
                "SEMI_FINISHED",
                "FROZEN",
                "PHOTO-QC-SMOKE-001"
        );
        assertThat(balances.getBody()).contains(
                "MD-SMOKE-20260706-003",
                "ZD-SMOKE-20260706-022",
                "FROZEN-QC",
                "FROZEN"
        );
    }

    @Test
    void dispatchesReworkThroughBusinessOperationApiAndMovesFrozenStock() {
        Map<String, Object> frozenDimension = Map.of(
                "ownerType", "CUSTOMER",
                "ownerCustomerId", 1002L,
                "customerId", 1002L,
                "motherOrderCode", "MD-SMOKE-20260706-004",
                "workOrderCode", "ZD-SMOKE-20260706-023",
                "materialCode", "MAT-SILVER-CHAIN",
                "warehouseCode", "FROZEN-QC",
                "stockStatus", "FROZEN"
        );
        Map<String, Object> seedFrozen = Map.of(
                "documentType", "quality_freeze",
                "documentCode", "QC-SMOKE-20260706-023",
                "to", frozenDimension,
                "quantity", new BigDecimal("6"),
                "weight", new BigDecimal("13.660"),
                "remark", "seed frozen stock for rework dispatch smoke test"
        );
        restTemplate.postForEntity("/api/inventory/ledger/transfers", seedFrozen, String.class);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reworkOrderCode", "FG-SMOKE-20260706-023");
        request.put("qualityCaseCode", "QC-SMOKE-20260706-023");
        request.put("motherOrderCode", "MD-SMOKE-20260706-004");
        request.put("workOrderCode", "ZD-SMOKE-20260706-023");
        request.put("customerId", 1002L);
        request.put("materialCode", "MAT-SILVER-CHAIN");
        request.put("fromWarehouseCode", "FROZEN-QC");
        request.put("toWarehouseCode", "REWORK-LINE-01");
        request.put("quantity", new BigDecimal("6"));
        request.put("weight", new BigDecimal("13.660"));
        request.put("process", "返工焊接");
        request.put("operator", "车间组长 马杰");
        request.put("reason", "焊点偏移返工");
        request.put("evidenceCode", "PHOTO-QC-SMOKE-002");
        request.put("remark", "rework dispatch smoke test");

        ResponseEntity<String> dispatched = restTemplate.postForEntity(
                "/api/business/operations/rework-dispatches",
                request,
                String.class
        );
        ResponseEntity<String> balances = restTemplate.getForEntity("/api/inventory/balances", String.class);

        assertThat(dispatched.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(dispatched.getBody()).contains(
                "FG-SMOKE-20260706-023",
                "QC-SMOKE-20260706-023",
                "FROZEN",
                "REWORK",
                "PHOTO-QC-SMOKE-002"
        );
        assertThat(balances.getBody()).contains(
                "MD-SMOKE-20260706-004",
                "ZD-SMOKE-20260706-023",
                "REWORK-LINE-01",
                "REWORK"
        );
    }

    @Test
    void reportsReworkThroughBusinessOperationApiAndMovesReworkStock() {
        Map<String, Object> reworkDimension = Map.of(
                "ownerType", "CUSTOMER",
                "ownerCustomerId", 1002L,
                "customerId", 1002L,
                "motherOrderCode", "MD-SMOKE-20260706-005",
                "workOrderCode", "ZD-SMOKE-20260706-024",
                "materialCode", "MAT-SILVER-CHAIN",
                "warehouseCode", "REWORK-LINE-01",
                "stockStatus", "REWORK"
        );
        Map<String, Object> seedRework = Map.of(
                "documentType", "rework_dispatch",
                "documentCode", "FG-SMOKE-20260706-024",
                "to", reworkDimension,
                "quantity", new BigDecimal("6"),
                "weight", new BigDecimal("13.660"),
                "remark", "seed rework stock for rework report smoke test"
        );
        restTemplate.postForEntity("/api/inventory/ledger/transfers", seedRework, String.class);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reworkOrderCode", "FG-SMOKE-20260706-024");
        request.put("qualityCaseCode", "QC-SMOKE-20260706-024");
        request.put("motherOrderCode", "MD-SMOKE-20260706-005");
        request.put("workOrderCode", "ZD-SMOKE-20260706-024");
        request.put("customerId", 1002L);
        request.put("materialCode", "MAT-SILVER-CHAIN");
        request.put("fromWarehouseCode", "REWORK-LINE-01");
        request.put("toWarehouseCode", "SEMI-FINISHED");
        request.put("quantity", new BigDecimal("6"));
        request.put("weight", new BigDecimal("13.660"));
        request.put("process", "返工焊接");
        request.put("operator", "王强");
        request.put("evidenceCode", "PHOTO-REWORK-SMOKE-001");
        request.put("remark", "rework report smoke test");

        ResponseEntity<String> reported = restTemplate.postForEntity(
                "/api/business/operations/rework-reports",
                request,
                String.class
        );
        ResponseEntity<String> balances = restTemplate.getForEntity("/api/inventory/balances", String.class);

        assertThat(reported.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(reported.getBody()).contains(
                "FG-SMOKE-20260706-024",
                "QC-SMOKE-20260706-024",
                "REWORK",
                "SEMI_FINISHED",
                "PHOTO-REWORK-SMOKE-001"
        );
        assertThat(balances.getBody()).contains(
                "MD-SMOKE-20260706-005",
                "ZD-SMOKE-20260706-024",
                "SEMI-FINISHED",
                "SEMI_FINISHED"
        );
    }

    @Test
    void releasesQualityStockThroughBusinessOperationApiAndMovesSemiFinishedToFinished() {
        Map<String, Object> semiFinishedDimension = Map.of(
                "ownerType", "CUSTOMER",
                "ownerCustomerId", 1002L,
                "customerId", 1002L,
                "motherOrderCode", "MD-SMOKE-20260706-006",
                "workOrderCode", "ZD-SMOKE-20260706-025",
                "materialCode", "MAT-SILVER-CHAIN",
                "warehouseCode", "SEMI-FINISHED",
                "stockStatus", "SEMI_FINISHED"
        );
        Map<String, Object> seedSemiFinished = Map.of(
                "documentType", "rework_report",
                "documentCode", "FG-SMOKE-20260706-025",
                "to", semiFinishedDimension,
                "quantity", new BigDecimal("6"),
                "weight", new BigDecimal("13.660"),
                "remark", "seed semi-finished for quality release smoke test"
        );
        restTemplate.postForEntity("/api/inventory/ledger/transfers", seedSemiFinished, String.class);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("qualityCaseCode", "QC-SMOKE-20260706-025");
        request.put("motherOrderCode", "MD-SMOKE-20260706-006");
        request.put("workOrderCode", "ZD-SMOKE-20260706-025");
        request.put("customerId", 1002L);
        request.put("materialCode", "MAT-SILVER-CHAIN");
        request.put("fromWarehouseCode", "SEMI-FINISHED");
        request.put("toWarehouseCode", "FINISHED-CUSTOMER");
        request.put("quantity", new BigDecimal("6"));
        request.put("weight", new BigDecimal("13.660"));
        request.put("process", "复检焊接");
        request.put("inspector", "周娜");
        request.put("releaseResult", "复检合格，允许成品入库");
        request.put("evidenceCode", "PHOTO-RELEASE-SMOKE-001");
        request.put("remark", "quality release smoke test");

        ResponseEntity<String> released = restTemplate.postForEntity(
                "/api/business/operations/quality-releases",
                request,
                String.class
        );
        ResponseEntity<String> balances = restTemplate.getForEntity("/api/inventory/balances", String.class);

        assertThat(released.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(released.getBody()).contains(
                "QC-SMOKE-20260706-025",
                "SEMI_FINISHED",
                "FINISHED",
                "PHOTO-RELEASE-SMOKE-001"
        );
        assertThat(balances.getBody()).contains(
                "MD-SMOKE-20260706-006",
                "ZD-SMOKE-20260706-025",
                "FINISHED-CUSTOMER",
                "FINISHED"
        );
    }

    @Test
    void shipsFinishedStockThroughBusinessOperationApiAndMovesFinishedToShipped() {
        Map<String, Object> finishedDimension = Map.of(
                "ownerType", "CUSTOMER",
                "ownerCustomerId", 1002L,
                "customerId", 1002L,
                "motherOrderCode", "MD-SMOKE-20260706-007",
                "workOrderCode", "ZD-SMOKE-20260706-026",
                "materialCode", "MAT-SILVER-CHAIN",
                "warehouseCode", "FINISHED-CUSTOMER",
                "stockStatus", "FINISHED"
        );
        Map<String, Object> seedFinished = Map.of(
                "documentType", "quality_release",
                "documentCode", "QC-SMOKE-20260706-026",
                "to", finishedDimension,
                "quantity", new BigDecimal("6"),
                "weight", new BigDecimal("13.660"),
                "remark", "seed finished stock for outbound shipment smoke test"
        );
        restTemplate.postForEntity("/api/inventory/ledger/transfers", seedFinished, String.class);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("shipmentCode", "OUT-SMOKE-20260706-026");
        request.put("motherOrderCode", "MD-SMOKE-20260706-007");
        request.put("workOrderCode", "ZD-SMOKE-20260706-026");
        request.put("customerId", 1002L);
        request.put("materialCode", "MAT-SILVER-CHAIN");
        request.put("fromWarehouseCode", "FINISHED-CUSTOMER");
        request.put("toWarehouseCode", "SHIPPED-CUSTOMER");
        request.put("quantity", new BigDecimal("6"));
        request.put("weight", new BigDecimal("13.660"));
        request.put("logisticsNo", "SF-OUT-SMOKE-001");
        request.put("operator", "库管员 何琳");
        request.put("evidenceCode", "ATT-OUT-SMOKE-001");
        request.put("remark", "outbound shipment smoke test");

        ResponseEntity<String> shipped = restTemplate.postForEntity(
                "/api/business/operations/outbound-shipments",
                request,
                String.class
        );
        ResponseEntity<String> balances = restTemplate.getForEntity("/api/inventory/balances", String.class);

        assertThat(shipped.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(shipped.getBody()).contains(
                "OUT-SMOKE-20260706-026",
                "FINISHED",
                "SHIPPED",
                "SF-OUT-SMOKE-001",
                "ATT-OUT-SMOKE-001"
        );
        assertThat(balances.getBody()).contains(
                "MD-SMOKE-20260706-007",
                "ZD-SMOKE-20260706-026",
                "SHIPPED-CUSTOMER",
                "SHIPPED"
        );
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
