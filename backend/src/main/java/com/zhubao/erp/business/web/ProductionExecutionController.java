package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.*;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.*;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/** 从已存母单和子单选择真实执行袋，不能把订单号当袋号。 */
@RestController
@RequestMapping("/api/business/production-targets")
public class ProductionExecutionController {
    private final ProductionExecutionService execution; private final BusinessDataAccess access; private final ActivityBagRepository bags;
    private final BagSplitService splits; private final DeliveryDraftRepository deliveries; private final OperatorContextResolver actors; private final PermissionGuard permissions;
    public ProductionExecutionController(ProductionExecutionService execution,BusinessDataAccess access,ActivityBagRepository bags,BagSplitService splits,DeliveryDraftRepository deliveries,OperatorContextResolver actors,PermissionGuard permissions) {
        this.execution=execution; this.access=access; this.bags=bags; this.splits=splits; this.deliveries=deliveries; this.actors=actors; this.permissions=permissions;
    }
    @GetMapping("/{target}") public ApiResponse<ProductionExecutionService.View> read(@PathVariable String target,HttpServletRequest request) { return ApiResponse.ok(execution.read(target,actor(request,target))); }
    @PostMapping("/{target}/starts") public ApiResponse<ProductionExecutionService.View> start(@PathVariable String target,@RequestBody Start body,HttpServletRequest request) {
        var actor=actor(request,target); permissions.assertAllowed(PermissionCode.BUSINESS_MOTHER_SPLIT,actor);
        return ApiResponse.ok(execution.start(body.requestId(),actor,target,body.steps()));
    }
    @GetMapping("/bags/{id}") public ApiResponse<ProductionExecutionService.BagDetail> bag(@PathVariable String id,HttpServletRequest request) { actor(request,target(id)); return ApiResponse.ok(execution.bag(id)); }
    @PostMapping("/bags/{id}/stock-releases") public ApiResponse<String> finish(@PathVariable String id,@RequestBody Version body,HttpServletRequest request) {
        var actor=actor(request,target(id)); permissions.assertAllowed(PermissionCode.BUSINESS_OUTBOUND_SHIPMENT,actor); execution.finish(body.requestId(),actor,id,body.version()); return ApiResponse.ok(id);
    }
    @PostMapping("/bags/{id}/splits") public ApiResponse<String> split(@PathVariable String id,@RequestBody Split body,HttpServletRequest request) {
        var actor=actor(request,target(id)); permissions.assertAllowed(PermissionCode.BUSINESS_PRODUCTION_REPORT,actor);
        if (deliveries.bagConfirmed(id)) throw new BusinessException("已发货袋不能再分袋");
        return ApiResponse.ok(splits.split(body.requestId(),actor.username(),id,body.quantities()));
    }
    private String target(String id) { return bags.find(id).orElseThrow(() -> new BusinessException("袋不存在")).targetCode(); }
    private OperatorContext actor(HttpServletRequest request,String target) {
        var actor=actors.requireSession(request);
        if (actor.role()==RoleCode.CUSTOMER_PORTAL) throw new PermissionDeniedException("客户账号不能进入内部加工办理");
        access.assertCanRead(actor,target); return actor;
    }
    public record Start(String requestId,List<String> steps) {}
    public record Version(String requestId,int version) {}
    public record Split(String requestId,List<BigDecimal> quantities) {}
}
