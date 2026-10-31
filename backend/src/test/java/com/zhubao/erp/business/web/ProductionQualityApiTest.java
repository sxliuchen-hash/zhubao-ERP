package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.app.PortionInspectionService;
import com.zhubao.erp.business.app.PortionReleaseService;
import com.zhubao.erp.business.app.ProcessAvailabilityQuery;
import com.zhubao.erp.business.app.QuantityDispositionService;
import com.zhubao.erp.business.app.ReworkPortionService;
import com.zhubao.erp.business.app.WeighingService;
import com.zhubao.erp.business.app.WholeBagReportService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBagWeightRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryPortionInspectionRepository;
import com.zhubao.erp.business.infra.InMemoryPortionReleaseRepository;
import com.zhubao.erp.business.infra.InMemoryQuantityDispositionRepository;
import com.zhubao.erp.business.infra.InMemoryReworkTaskRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.business.infra.InMemoryWholeBagReportRepository;
import com.zhubao.erp.common.error.GlobalExceptionHandler;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductionQualityApiTest {

    @Mock
    private OperatorContextResolver actors;

    private MockMvc mvc;
    private ActivityBag bag;

    @BeforeEach
    void setUp() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryWholeBagReportRepository reports = new InMemoryWholeBagReportRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        InMemoryQuantityDispositionRepository dispositions = new InMemoryQuantityDispositionRepository();
        InMemoryPortionInspectionRepository inspections = new InMemoryPortionInspectionRepository();
        InMemoryPortionReleaseRepository releases = new InMemoryPortionReleaseRepository();
        InMemoryReworkTaskRepository reworks = new InMemoryReworkTaskRepository();
        InMemoryBagWeightRepository weightRecords = new InMemoryBagWeightRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        PermissionGuard guard = new PermissionGuard();
        ActivityBagService bagService = new ActivityBagService(bags);
        bag = bagService.open("MD-20261030-0801", "牙骨", "REQ-1", "LINE-1",
                new BigDecimal("500"), StockMeasurement.unknown(), null);
        routes.insert(new RouteSnapshot("RS-1", BusinessKind.PROCESSING, bag.targetCode(), 1,
                List.of("CUT", "BLANK"), true), null);
        documents.saveAttachment(new EvidenceAttachment(
                "ATT-1", "DOC-1", "来料母单", bag.targetCode(), "a.jpg", "image/jpeg",
                "local://a", "hot", "PWA 拍照上传", "已上传", "操作工", "sha256:a",
                "2026-10-30 10:00:00", "无", "green", EvidenceCodes.MOTHER_ORDER));
        ProcessAvailabilityQuery query = new ProcessAvailabilityQuery(bags, routes, portions, guard, dispositions);
        WholeBagReportService wholeBag = new WholeBagReportService(
                bags, query, portions, portions, reports, documents, new com.zhubao.erp.document.infra.InMemoryBusinessEvidenceLinkRepository(), unit);
        StockLedgerService stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
        QuantityDispositionService dispositionService = new QuantityDispositionService(
                bags, bagService, routes, query, portions, portions, dispositions, documents, stocks, unit);
        WeighingService weighing = new WeighingService(bags, weightRecords, unit);
        PortionInspectionService inspectionService = new PortionInspectionService(bags, inspections, guard, unit);
        PortionReleaseService releaseService = new PortionReleaseService(
                bags, inspections, releases, routes, portions, weighing, guard, unit);
        ReworkPortionService reworkService = new ReworkPortionService(
                bags, bagService, inspections, inspectionService, reworks, guard, unit);
        ProductionQualityController controller = new ProductionQualityController(
                query, wholeBag, dispositionService, weighing, inspectionService, releaseService, reworkService, actors);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 错袋过期版本空客户范围会被拒绝且原请求回放同一批次() throws Exception {
        when(actors.resolve(any())).thenReturn(new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL));
        mvc.perform(get("/api/business/production-quality/bags/BAG-MISSING/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("袋不存在"));
        mvc.perform(get("/api/business/production-quality/bags/" + bag.id() + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(500))
                .andExpect(jsonPath("$.data.bagVersion").value(1))
                .andExpect(jsonPath("$.data.stepId").value("CUT"));

        String body = """
                {"requestId":"http-c08-01","bagId":"%s","bagVersion":1,"expectedStepId":"CUT","quantity":{"confirmedQuantity":500},"attachmentCode":"","progressPercent":null}
                """.formatted(bag.id());
        mvc.perform(post("/api/business/production-quality/whole-bag-reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("没有有效的现场照片"));

        String ready = body.replace("\"attachmentCode\":\"\"", "\"attachmentCode\":\"ATT-1\"");
        String first = mvc.perform(post("/api/business/production-quality/whole-bag-reports").contentType(MediaType.APPLICATION_JSON).content(ready))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepId").value("CUT"))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/business/production-quality/whole-bag-reports").contentType(MediaType.APPLICATION_JSON).content(ready))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThatBatch(first, second);

        String stale = ready.replace("\"bagVersion\":1", "\"bagVersion\":9").replace("http-c08-01", "http-c08-02");
        mvc.perform(post("/api/business/production-quality/whole-bag-reports").contentType(MediaType.APPLICATION_JSON).content(stale))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("袋已被其他人修改，请刷新后重试"));

        when(actors.resolve(any())).thenReturn(new OperatorContext(
                "业务", RoleCode.BUSINESS_MANAGER, DataScope.CUSTOMER_RANGE, false, "", List.of(), List.of("REPORT")));
        mvc.perform(post("/api/business/production-quality/whole-bag-reports").contentType(MediaType.APPLICATION_JSON).content(ready))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("客户范围为空，不能报工"));
    }

    private static void assertThatBatch(String first, String second) {
        String marker = "\"batchCode\":\"";
        String left = first.substring(first.indexOf(marker) + marker.length());
        String right = second.substring(second.indexOf(marker) + marker.length());
        org.assertj.core.api.Assertions.assertThat(left.substring(0, left.indexOf('"'))).isEqualTo(right.substring(0, right.indexOf('"')));
    }
}
