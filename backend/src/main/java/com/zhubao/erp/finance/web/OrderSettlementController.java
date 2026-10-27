package com.zhubao.erp.finance.web;

import com.zhubao.erp.business.app.OrderDeliveryCompletionQuery;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment.LaborFact;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.app.FinanceReadAuthorizer;
import com.zhubao.erp.finance.app.OrderSettlementService;
import com.zhubao.erp.finance.domain.OrderSettlement;
import com.zhubao.erp.finance.domain.OrderSettlementItem;
import com.zhubao.erp.finance.domain.OrderSettlementRepository;
import com.zhubao.erp.finance.domain.SettlementCommand;
import com.zhubao.erp.finance.domain.SettlementItemKind;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交货后结单办理。普通订单和扫码不走这里，员工工费只返回给工资权限。
 */
@RestController
@RequestMapping("/api/finance/order-settlements")
public class OrderSettlementController {

    private final OrderSettlementService settlements;
    private final OrderSettlementRepository repository;
    private final OrderDeliveryCompletionQuery deliveries;
    private final PermissionGuard permissionGuard;
    private final OperatorContextResolver operators;
    private final FinanceReadAuthorizer authorizer;

    public OrderSettlementController(
            OrderSettlementService settlements,
            OrderSettlementRepository repository,
            OrderDeliveryCompletionQuery deliveries,
            PermissionGuard permissionGuard,
            OperatorContextResolver operators,
            FinanceReadAuthorizer authorizer
    ) {
        this.settlements = settlements;
        this.repository = repository;
        this.deliveries = deliveries;
        this.permissionGuard = permissionGuard;
        this.operators = operators;
        this.authorizer = authorizer;
    }

    @GetMapping("/context")
    public ApiResponse<SettlementContextView> context(
            @RequestParam String targetType,
            @RequestParam String targetCode,
            HttpServletRequest request
    ) {
        OperatorContext actor = actor(request);
        OrderDeliveryAssessment assessment = deliveries.assess(targetType, targetCode);
        authorizer.assertCustomer(actor, assessment.customerId(), assessment.customerName());
        boolean payroll = canSeePayroll(actor);
        OrderSettlement current = repository.latest(targetType, targetCode, null).orElse(null);
        List<OrderSettlementItem> items = current == null ? List.of() : repository.items(current.code(), null);
        return ApiResponse.ok(new SettlementContextView(
                assessment.eligible(),
                assessment.feeTarget(),
                assessment.deliveryState(),
                assessment.deliveryRevision(),
                assessment.targetType(),
                assessment.targetCode(),
                assessment.motherOrderCode(),
                assessment.customerId(),
                assessment.customerName(),
                assessment.blocks().stream().map(block -> block.message()).toList(),
                payroll ? assessment.laborFacts().stream().map(LaborView::from).toList() : List.of(),
                current == null ? null : view(current, items, payroll)
        ));
    }

    @GetMapping("/{code}")
    public ApiResponse<SettlementView> detail(@PathVariable String code, HttpServletRequest request) {
        OperatorContext actor = actor(request);
        OrderSettlement settlement = repository.find(code, null).orElseThrow(() -> new BusinessException("结单不存在"));
        authorizer.assertCustomer(actor, settlement.customerId(), settlement.customerName());
        return ApiResponse.ok(view(settlement, repository.items(code, null), canSeePayroll(actor)));
    }

    @PostMapping("/drafts")
    public ApiResponse<OrderSettlement> draft(@RequestBody SettlementCommand command, HttpServletRequest request) {
        OperatorContext actor = actor(request);
        return ApiResponse.ok(settlements.saveDraft(command, actor, permissions(actor)));
    }

    @PostMapping("/confirmations")
    public ApiResponse<OrderSettlement> confirm(@RequestBody SettlementCommand command, HttpServletRequest request) {
        OperatorContext actor = actor(request);
        return ApiResponse.ok(settlements.confirm(command, actor, permissions(actor)));
    }

    @PostMapping("/adjustments")
    public ApiResponse<OrderSettlement> adjust(@RequestBody SettlementCommand command, HttpServletRequest request) {
        OperatorContext actor = actor(request);
        return ApiResponse.ok(settlements.adjust(command, actor, permissions(actor)));
    }

    private OperatorContext actor(HttpServletRequest request) {
        OperatorContext context = operators.resolve(request);
        authorizer.assertSignedIn(request, context);
        return context;
    }

    private Set<String> permissions(OperatorContext actor) {
        return permissionGuard.effectivePermissions(actor.role(), actor.coverDuties()).stream()
                .map(PermissionCode::name)
                .collect(Collectors.toSet());
    }

    private boolean canSeePayroll(OperatorContext actor) {
        return actor.dataScope() == DataScope.ALL && permissions(actor).contains(PermissionCode.FINANCE_PAYROLL.name());
    }

    private static SettlementView view(OrderSettlement settlement, List<OrderSettlementItem> items, boolean payroll) {
        List<OrderSettlementItem> visible = payroll
                ? items
                : items.stream().filter(item -> item.kind() == SettlementItemKind.CUSTOMER).toList();
        return new SettlementView(
                settlement.code(), settlement.status().name(), settlement.targetType(), settlement.targetCode(),
                settlement.rowVersion(), settlement.customerNet(), payroll ? settlement.employeeNet() : null, visible
        );
    }

    public record SettlementContextView(
            boolean eligible,
            boolean feeTarget,
            String deliveryState,
            String deliveryRevision,
            String targetType,
            String targetCode,
            String motherOrderCode,
            Long customerId,
            String customerName,
            List<String> blocks,
            List<LaborView> labor,
            SettlementView current
    ) {
    }

    public record LaborView(
            String sourceRef,
            String processName,
            BigDecimal quantity,
            BigDecimal measuredWeight,
            boolean weightOnly
    ) {
        static LaborView from(LaborFact fact) {
            return new LaborView(fact.sourceRef(), fact.processName(), fact.quantity(), fact.measuredWeight(), fact.weightOnly());
        }
    }

    public record SettlementView(
            String code,
            String status,
            String targetType,
            String targetCode,
            int rowVersion,
            BigDecimal customerNet,
            BigDecimal employeeNet,
            List<OrderSettlementItem> items
    ) {
    }
}
