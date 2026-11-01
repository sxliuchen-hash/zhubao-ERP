package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.*;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.label.app.ShippingBagLabelService;
import com.zhubao.erp.label.domain.ShippingBagLabelPreview;
import com.zhubao.erp.scan.app.PublicDeliveryAccessService;
import com.zhubao.erp.security.app.*;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/** 独立交付的实际办理入口，沿用领域服务的库存、回放及事务规则。 */
@RestController
@RequestMapping("/api/business/deliveries")
public class ShippingDeliveryController {
    private final DeliveryDraftService creation;
    private final DeliveryConfirmationService confirmation;
    private final DeliveryCorrectionService correction;
    private final CustomerReturnSourceService returns;
    private final DeliveryDraftRepository drafts;
    private final CustomerReturnRepository returnRows;
    private final DeliveryDataAccess access;
    private final BusinessDataAccess businessAccess;
    private final OperatorContextResolver actors;
    private final PermissionGuard permissions;
    private final PublicDeliveryAccessService publicAccess;
    private final ShippingBagLabelService labels;
    public ShippingDeliveryController(DeliveryDraftService creation, DeliveryConfirmationService confirmation,
            DeliveryCorrectionService correction, CustomerReturnSourceService returns, DeliveryDraftRepository drafts,
            CustomerReturnRepository returnRows, DeliveryDataAccess access, BusinessDataAccess businessAccess,
            OperatorContextResolver actors, PermissionGuard permissions, PublicDeliveryAccessService publicAccess,
            ShippingBagLabelService labels) {
        this.creation=creation; this.confirmation=confirmation; this.correction=correction; this.returns=returns;
        this.drafts=drafts; this.returnRows=returnRows; this.access=access; this.businessAccess=businessAccess;
        this.actors=actors; this.permissions=permissions; this.publicAccess=publicAccess; this.labels=labels;
    }
    @PostMapping public ApiResponse<DeliveryDraftSheet> open(@RequestBody Open body, HttpServletRequest request) {
        var actor=actor(request, PermissionCode.BUSINESS_OUTBOUND_SHIPMENT);
        for (var line : body.selections()==null ? List.<DeliverySourceSelection>of() : body.selections()) businessAccess.assertCanRead(actor, line.targetCode());
        return ApiResponse.ok(creation.open(body.requestId(), actor.username(), body.customerId(), body.logisticsNote(), body.remark(), body.selections()));
    }
    @GetMapping("/{id}") public ApiResponse<Detail> read(@PathVariable String id, HttpServletRequest request) {
        var draft=access.require(actors.requireSession(request), id);
        return ApiResponse.ok(new Detail(draft, drafts.lines(id), drafts.confirmedSources(id), drafts.confirmedSources(id).stream().flatMap(source -> returnRows.listByOutbound(source.outboundCode()).stream()).toList()));
    }
    @PostMapping("/{id}/confirmations") public ApiResponse<DeliveryConfirmation> confirm(@PathVariable String id, @RequestBody Version body, HttpServletRequest request) {
        var actor=actor(request, PermissionCode.BUSINESS_OUTBOUND_SHIPMENT); access.require(actor,id);
        return ApiResponse.ok(confirmation.confirm(body.requestId(), actor.username(), id, body.version()));
    }
    @GetMapping("/{id}/impact") public ApiResponse<DeliveryImpact> impact(@PathVariable String id, HttpServletRequest request) {
        access.require(actors.requireSession(request),id); return ApiResponse.ok(correction.impact(id));
    }
    @PostMapping("/{id}/corrections") public ApiResponse<DeliveryDraft> correct(@PathVariable String id, @RequestBody Correction body, HttpServletRequest request) {
        var actor=actor(request, PermissionCode.BUSINESS_OUTBOUND_SHIPMENT); access.require(actor,id);
        return ApiResponse.ok(correction.apply(body.requestId(), actor.username(), id, body.version(), body.intent()));
    }
    @PostMapping("/{id}/returns") public ApiResponse<CustomerReturnSource> receive(@PathVariable String id, @RequestBody Return body, HttpServletRequest request) {
        var actor=actor(request, PermissionCode.BUSINESS_CUSTOMER_RETURN); access.require(actor,id);
        return ApiResponse.ok(returns.receive(body.requestId(), actor.username(), id, body.outboundCode(), body.quantity(), body.weight()));
    }
    @PostMapping("/{id}/public-links") public ApiResponse<String> publish(@PathVariable String id, @RequestBody Publish body, HttpServletRequest request) {
        var actor=actor(request, PermissionCode.BUSINESS_OUTBOUND_SHIPMENT); access.require(actor,id);
        if (body.photoIds()!=null && !body.photoIds().isEmpty() && actor.role()!=RoleCode.ADMIN)
            throw new PermissionDeniedException("只有管理员可明确公开出库照片");
        return ApiResponse.ok(publicAccess.open(body.requestId(), actor.username(), id, body.photoIds()));
    }
    @PostMapping("/{id}/public-links/{token}/disable") public ApiResponse<String> disable(@PathVariable String id, @PathVariable String token, @RequestBody Version body, HttpServletRequest request) {
        var actor=actor(request, PermissionCode.BUSINESS_OUTBOUND_SHIPMENT); access.require(actor,id);
        publicAccess.readForDelivery(token,id); publicAccess.disable(body.requestId(), actor.username(),token); return ApiResponse.ok(token);
    }
    @GetMapping("/{id}/labels/{lineNo}") public ApiResponse<ShippingBagLabelPreview> label(@PathVariable String id, @PathVariable int lineNo, @RequestParam String accessId, HttpServletRequest request) {
        var actor=actor(request,PermissionCode.LABEL_PRINT); access.require(actor,id);
        return ApiResponse.ok(labels.preview(id,lineNo,accessId));
    }
    private OperatorContext actor(HttpServletRequest request, PermissionCode permission) {
        var actor=actors.requireSession(request); permissions.assertAllowed(permission,actor); return actor;
    }
    public record Open(String requestId,long customerId,String logisticsNote,String remark,List<DeliverySourceSelection> selections) {}
    public record Version(String requestId,int version) {}
    public record Correction(String requestId,int version,DeliveryCorrectionIntent intent) {}
    public record Return(String requestId,String outboundCode,BigDecimal quantity,BigDecimal weight) {}
    public record Publish(String requestId,List<String> photoIds) {}
    public record Detail(DeliveryDraft draft,List<DeliveryDraftLine> lines,List<DeliveryConfirmedSource> sources,List<CustomerReturnSource> returns) {}
}
