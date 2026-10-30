package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.app.BagSplitService;
import com.zhubao.erp.business.app.OutsourcingDispatchService;
import com.zhubao.erp.business.app.OutsourcingQueryService;
import com.zhubao.erp.business.app.OutsourcingReturnService;
import com.zhubao.erp.business.app.OutsourcingSourceService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.OutsourcingCounterpart;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingSourceRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.common.error.GlobalExceptionHandler;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
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
class OutsourcingApiTest {

    @Mock
    private OperatorContextResolver actors;

    private MockMvc mvc;
    private OutsourcingTask task;
    private ActivityBag bag;
    private InMemoryOutsourcingDispatchRepository dispatchRows;

    @BeforeEach
    void setUp() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryOutsourcingSourceRepository sources = new InMemoryOutsourcingSourceRepository();
        dispatchRows = new InMemoryOutsourcingDispatchRepository();
        InMemoryOutsourcingReturnRepository returns = new InMemoryOutsourcingReturnRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        PermissionGuard guard = new PermissionGuard();
        ActivityBagService bagService = new ActivityBagService(bags);
        bag = new ActivityBag("BAG-D05", "MD-20261030-0501", "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("200"), StockMeasurement.unknown(), 1);
        unit.execute(new com.zhubao.erp.work.domain.CommandCall(
                "d05-bag-01", "clerk-01", "SAVE_BAG", "BAG", bag.id(), bag.id()), session -> {
            bags.save(bag, 0, session);
            return bag.id();
        });
        routes.insert(new RouteSnapshot("RS-D05", BusinessKind.PROCESSING, bag.targetCode(), 1,
                ProcessCatalog.defaults(BusinessKind.PROCESSING, "牙骨").stream().map(ProcessStep::id).toList(), true), null);
        OutsourcingSourceService sourceService = new OutsourcingSourceService(sources, bags, routes, unit);
        sourceService.register("d05-reg-01", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-POLISH-01", "甲抛光", "clerk-01");
        task = sourceService.openTask("d05-task01", "WX-POLISH-01", bag.targetCode(), "POLISH_BEAD",
                bag.id(), OwnerType.CUSTOMER, "10086", "clerk-01");
        documents.saveAttachment(new EvidenceAttachment(
                "ATT-OTHER", "DOC-1", "来料母单", "MD-20261030-9999", "a.jpg", "image/jpeg",
                "local://a", "hot", "上传", "已上传", "操作工", "sha256:a",
                "2026-10-30 10:00:00", "无", "green", EvidenceCodes.MOTHER_ORDER));
        BagSplitService splits = new BagSplitService(bags, bagService, unit);
        OutsourcingDispatchService dispatchService = new OutsourcingDispatchService(
                sources, dispatchRows, returns, bags, splits, unit);
        OutsourcingReturnService returnService = new OutsourcingReturnService(
                dispatchRows, returns, sources, bags, splits, unit);
        OutsourcingQueryService query = new OutsourcingQueryService(
                sources, dispatchRows, returns, bags, dispatchService, returnService, documents, guard);
        OutsourcingController controller = new OutsourcingController(
                query, dispatchService, returnService, sources, guard, actors);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 在外不是厂内现货且错客户错发外行未授权附件被拒绝() throws Exception {
        when(actors.resolve(any())).thenReturn(new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL));
        String dispatchBody = """
                {"requestId":"d05-dsp-01","taskId":"%s","bagId":"%s","bagVersion":1,"quantity":200}
                """.formatted(task.taskId(), bag.id());
        mvc.perform(post("/api/business/outsourcing/dispatches").contentType(MediaType.APPLICATION_JSON).content(dispatchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(200));
        mvc.perform(get("/api/business/outsourcing/tasks/" + task.taskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inHouseQuantity").value(0))
                .andExpect(jsonPath("$.data.outQuantity").value(200))
                .andExpect(jsonPath("$.data.pendingQuantity").value(200))
                .andExpect(jsonPath("$.data.outsourcingCountsAsInHouseStock").value(false))
                .andExpect(jsonPath("$.data.ownerRef").value("10086"))
                .andExpect(jsonPath("$.data.partyName").value("甲抛光"));
        mvc.perform(get("/api/business/outsourcing/tasks/" + task.taskId()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(dispatchRows.listByTask(task.taskId())).hasSize(1);

        String dispatchId = dispatchRows.listByTask(task.taskId()).get(0).dispatchId();
        String returnBody = """
                {"requestId":"d05-ret-01","dispatchId":"%s","dispatchVersion":1,"received":150,"unprocessed":0,"loss":5,"weight":null}
                """.formatted(dispatchId);
        mvc.perform(post("/api/business/outsourcing/returns").contentType(MediaType.APPLICATION_JSON).content(returnBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedQty").value(150));
        mvc.perform(get("/api/business/outsourcing/tasks/" + task.taskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inHouseQuantity").value(150))
                .andExpect(jsonPath("$.data.outQuantity").value(45))
                .andExpect(jsonPath("$.data.pendingQuantity").value(45))
                .andExpect(jsonPath("$.data.outsourcingCountsAsInHouseStock").value(false))
                .andExpect(jsonPath("$.data.returns[0].lossQty").value(5));
        org.assertj.core.api.Assertions.assertThat(dispatchRows.listByTask(task.taskId())).hasSize(1);

        mvc.perform(get("/api/business/outsourcing/dispatches/OD-MISSING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("外协发外不存在"));
        mvc.perform(get("/api/business/outsourcing/tasks/" + task.taskId() + "?attachmentCode=ATT-OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未授权附件"));
        mvc.perform(get("/api/business/outsourcing/tasks/" + task.taskId() + "?attachmentCode=ATT-MISSING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未授权附件"));

        when(actors.resolve(any())).thenReturn(new OperatorContext(
                "管理员", RoleCode.ADMIN, DataScope.CUSTOMER_RANGE, false, "", List.of("8")));
        mvc.perform(get("/api/business/outsourcing/tasks/" + task.taskId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("客户不在当前账号范围内"));
        when(actors.resolve(any())).thenReturn(new OperatorContext(
                "管理员", RoleCode.ADMIN, DataScope.CUSTOMER_RANGE, false, "", List.of()));
        mvc.perform(get("/api/business/outsourcing/dispatches/" + dispatchId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("客户范围账号没有绑定客户"));
        when(actors.resolve(any())).thenReturn(new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL));
        mvc.perform(get("/api/business/outsourcing/tasks/" + task.taskId()))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(dispatchRows.listByTask(task.taskId())).hasSize(1);
    }
}
