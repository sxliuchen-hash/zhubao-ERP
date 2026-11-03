package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.OutsourcingDispatchService;
import com.zhubao.erp.business.app.OutsourcingQueryService;
import com.zhubao.erp.business.app.OutsourcingReturnService;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingPositionView;
import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 外协查询和办理入口。只转调已有发外、收回和查询，不另写库存或账务。
 */
@RestController
@RequestMapping("/api/business/outsourcing")
public class OutsourcingController {

    private final com.zhubao.erp.business.app.OutsourcingCompletionService completions;
    private final com.zhubao.erp.business.domain.OutsourcingReturnRepository returnRows;
    private final OutsourcingQueryService query;
    private final OutsourcingDispatchService dispatches;
    private final OutsourcingReturnService returns;
    private final OutsourcingSourceRepository sources;
    private final PermissionGuard permissions;
    private final OperatorContextResolver actors;

    public OutsourcingController(OutsourcingQueryService query, OutsourcingDispatchService dispatches,
                                 OutsourcingReturnService returns, OutsourcingSourceRepository sources,
                                 PermissionGuard permissions, OperatorContextResolver actors) {
        this(query, dispatches, returns, sources, permissions, actors, null, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public OutsourcingController(OutsourcingQueryService query, OutsourcingDispatchService dispatches,
            OutsourcingReturnService returns, OutsourcingSourceRepository sources, PermissionGuard permissions,
            OperatorContextResolver actors, com.zhubao.erp.business.app.OutsourcingCompletionService completions,
            com.zhubao.erp.business.domain.OutsourcingReturnRepository returnRows) {
        this.completions=completions; this.returnRows=returnRows;
        this.query = query;
        this.dispatches = dispatches;
        this.returns = returns;
        this.sources = sources;
        this.permissions = permissions;
        this.actors = actors;
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<OutsourcingPositionView> position(@PathVariable String taskId,
                                                         @RequestParam(required = false) String attachmentCode,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(query.position(taskId, actors.resolve(request), attachmentCode));
    }

    @GetMapping("/dispatches/{dispatchId}")
    public ApiResponse<OutsourcingDispatch> dispatch(@PathVariable String dispatchId, HttpServletRequest request) {
        return ApiResponse.ok(query.dispatch(dispatchId, actors.resolve(request)));
    }

    @PostMapping("/dispatches")
    public ApiResponse<OutsourcingDispatch> dispatch(@RequestBody DispatchRequest command, HttpServletRequest request) {
        OperatorContext actor = maintain(request);
        OutsourcingTask task = sources.findTask(command.taskId()).orElseThrow(() -> new BusinessException("外协任务不存在"));
        assertOwner(actor, task);
        return ApiResponse.ok(dispatches.dispatch(
                command.requestId(), actor.operator(), command.taskId(), command.bagId(), command.bagVersion(), command.quantity()));
    }

    @PostMapping("/returns")
    public ApiResponse<OutsourcingReturn> receive(@RequestBody ReturnRequest command, HttpServletRequest request) {
        OperatorContext actor = maintain(request);
        OutsourcingDispatch dispatch = query.dispatch(command.dispatchId(), actor);
        return ApiResponse.ok(returns.receive(
                command.requestId(), actor.operator(), dispatch.dispatchId(), command.dispatchVersion(),
                command.received(), command.unprocessed(), command.loss(),
                command.weight() == null ? null : StockMeasurement.known(command.weight())));
    }

    @GetMapping("/tasks/{taskId}/returns")
    public ApiResponse<java.util.List<ReturnView>> returnList(@PathVariable String taskId,HttpServletRequest request) {
        var view=query.position(taskId,actors.resolve(request),null);
        return ApiResponse.ok(view.dispatches().stream().flatMap(dispatch -> returnRows.listByDispatch(dispatch.dispatchId()).stream())
                .map(row -> new ReturnView(row,completions.findByReturn(row.returnId()).isPresent())).toList());
    }
    public record ReturnView(com.zhubao.erp.business.domain.OutsourcingReturn returned,boolean completed) {}

    @PostMapping("/completions")
    public ApiResponse<com.zhubao.erp.business.domain.OutsourcingCompletion> complete(@RequestBody CompletionRequest body, HttpServletRequest request) {
        var actor=maintain(request);
        var returned=returnRows.find(body.returnId()).orElseThrow(() -> new BusinessException("外协收回不存在"));
        query.dispatch(returned.dispatchId(),actor);
        return ApiResponse.ok(completions.confirm(body.requestId(),actor.username(),returned.returnId(),body.amount()));
    }
    public record CompletionRequest(String requestId,String returnId,java.math.BigDecimal amount) {}

    private OperatorContext maintain(HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        RefactorPermissionContract.assertAction(permissions, PermissionCode.OUTSOURCING_MAINTAIN, actor);
        return actor;
    }

    private static void assertOwner(OperatorContext actor, OutsourcingTask task) {
        Long customerId = null;
        if (task.ownerType() == OwnerType.CUSTOMER) {
            try {
                customerId = Long.valueOf(task.ownerRef());
            } catch (NumberFormatException ex) {
                throw new BusinessException("缺少物料权属");
            }
        }
        RefactorPermissionContract.assertCustomerRead(actor, customerId);
    }

    public record DispatchRequest(String requestId, String taskId, String bagId, int bagVersion, BigDecimal quantity) {
    }

    public record ReturnRequest(String requestId, String dispatchId, int dispatchVersion,
                                BigDecimal received, BigDecimal unprocessed, BigDecimal loss, BigDecimal weight) {
    }
}
