package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.*;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.common.error.GlobalExceptionHandler;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.label.app.ShippingBagLabelService;
import com.zhubao.erp.scan.app.PublicDeliveryAccessService;
import com.zhubao.erp.scan.infra.InMemoryPublicDeliveryAccessRepository;
import com.zhubao.erp.scan.web.PublicDeliveryController;
import com.zhubao.erp.security.app.*;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

class ShippingDeliveryApiTest {
    @Test void 真正入口完成建稿确认扫码且内部资料受客户及岗位权限约束() throws Exception {
        var documents=new InMemoryBusinessDocumentRepository();
        String target="MD-20261101-1001";
        documents.saveMotherOrder(new MotherOrderRecord(target,7L,7L,"牙骨",null,null,null,null,null,null,"8mm珠料","CUSTOMER",true,"WH",BigDecimal.TEN,"颗",BigDecimal.ONE,null,null,null,null,null,"clerk","","","OPEN","2026-11-01","抛光后穿串","2026-11-30","客户甲"));
        var bags=new InMemoryActivityBagRepository();
        bags.save(new ActivityBag("BAG-API",target,"牙骨","REQ-1","LINE-1","",false,BigDecimal.TEN,StockMeasurement.known(BigDecimal.ONE),1),0,null);
        var drafts=new InMemoryDeliveryDraftRepository(); var stock=new StockLedgerService(new InMemoryStockLedgerRepository());
        var unit=new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var place=new DocumentDeliveryStockPlaceLookup(documents);
        var creation=new DeliveryDraftService(drafts,bags,new DocumentProcessingTargetCustomer(documents),id -> true,unit,documents);
        var confirmation=new DeliveryConfirmationService(drafts,bags,place,id -> true,stock,documents,unit);
        var returned=new InMemoryCustomerReturnRepository();
        var returns=new CustomerReturnSourceService(drafts,place,returned,documents,stock,unit);
        DeliveryDownstream downstream=new DeliveryDownstream() {
            public BigDecimal consumedQuantity(String code) { return BigDecimal.ZERO; }
            public DownstreamFact chargeFact(String code) { return DownstreamFact.ABSENT; }
            public DownstreamFact refundFact(String code) { return DownstreamFact.UNDECIDED; }
        };
        var correction=new DeliveryCorrectionService(drafts,downstream,unit);
        var business=new BusinessDataAccess(documents,stock); var access=new DeliveryDataAccess(drafts,business);
        var publicAccess=new PublicDeliveryAccessService(drafts,new InMemoryPublicDeliveryAccessRepository(),unit);
        var labels=new ShippingBagLabelService(drafts,publicAccess); var actors=mock(OperatorContextResolver.class);
        var admin=new OperatorContext("管理员",RoleCode.ADMIN,DataScope.ALL,false,"",List.of(),List.of(),"admin","","");
        when(actors.requireSession(any())).thenReturn(admin);
        var controller=new ShippingDeliveryController(creation,confirmation,correction,returns,drafts,returned,access,business,actors,new PermissionGuard(),publicAccess,labels);
        var mvc=MockMvcBuilders.standaloneSetup(controller,new PublicDeliveryController(publicAccess)).setControllerAdvice(new GlobalExceptionHandler()).build();
        stock.transfer(new StockTransferCommand("quality_release","READY",null,new StockDimension(OwnerType.CUSTOMER,7L,7L,target,target,"牙骨","WH",StockStatus.FINISHED),BigDecimal.TEN,BigDecimal.ONE,"前置合格成品"));
        String opened=mvc.perform(post("/api/business/deliveries").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"api-draft-01\",\"customerId\":7,\"logisticsNote\":\"顺丰\",\"remark\":\"先发这袋\",\"selections\":[{\"bagId\":\"BAG-API\",\"bagVersion\":1,\"targetCode\":\""+target+"\",\"quantityUnit\":\"颗\"}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(opened).path("data").path("draft").path("deliveryId").asText();
        mvc.perform(post("/api/business/deliveries/"+id+"/confirmations").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"api-confirm-01\",\"version\":1}")).andExpect(status().isOk());
        String published=mvc.perform(post("/api/business/deliveries/"+id+"/public-links").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"api-public-01\",\"photoIds\":[]}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(published).path("data").asText();
        mvc.perform(get("/api/public/deliveries/"+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.customerName").value("客户甲")).andExpect(jsonPath("$.data.bags[0].requirement").value("抛光后穿串"));
        mvc.perform(get("/api/business/deliveries/"+id+"/labels/1").param("accessId",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.payload.qrContent").value("/d/"+token+"?bag=1"));
        assertThat(documents.listOutboundOrders()).hasSize(1);
        when(actors.requireSession(any())).thenReturn(new OperatorContext("其他客户",RoleCode.ADMIN,DataScope.CUSTOMER_RANGE,false,"",List.of("8"),List.of(),"other","",""));
        mvc.perform(get("/api/business/deliveries/"+id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/business/deliveries/"+id+"/labels/1").param("accessId",token)).andExpect(status().isForbidden());
        when(actors.requireSession(any())).thenReturn(new OperatorContext("客户",RoleCode.CUSTOMER_PORTAL,DataScope.ALL));
        mvc.perform(get("/api/business/deliveries/"+id)).andExpect(status().isForbidden());
        when(actors.requireSession(any())).thenReturn(new OperatorContext("操作工",RoleCode.OPERATOR,DataScope.ALL));
        mvc.perform(post("/api/business/deliveries/"+id+"/confirmations").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"wrong-role-01\",\"version\":2}")).andExpect(status().isForbidden());
        when(actors.requireSession(any())).thenReturn(admin);
        mvc.perform(post("/api/business/deliveries/"+id+"/public-links/"+token+"/disable").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"api-disable-01\",\"version\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/public/deliveries/"+token)).andExpect(status().isBadRequest());
        assertThat(documents.listOutboundOrders()).hasSize(1);
    }
}
