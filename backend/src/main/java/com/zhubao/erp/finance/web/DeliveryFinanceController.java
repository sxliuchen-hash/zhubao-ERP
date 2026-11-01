package com.zhubao.erp.finance.web;

import com.zhubao.erp.business.app.*;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.app.*;
import com.zhubao.erp.finance.domain.*;
import com.zhubao.erp.security.app.*;
import com.zhubao.erp.security.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/** 客户按次出账和员工整单核算分别授权、分别确认，不由一次发货触发工资。 */
@RestController
@RequestMapping("/api/finance")
public class DeliveryFinanceController {
    private final DeliveryDataAccess deliveries;
    private final BusinessDataAccess business;
    private final DeliveryDraftRepository drafts;
    private final CustomerDeliveryBillingService billing;
    private final CustomerDeliveryBillRepository bills;
    private final EmployeeTargetClosureLookup closures;
    private final EmployeeTargetSettlementService employees;
    private final ProcessingTargetCustomer customers;
    private final FinanceRepository finance;
    private final OperatorContextResolver actors;
    private final PermissionGuard permissions;
    public DeliveryFinanceController(DeliveryDataAccess deliveries, BusinessDataAccess business, DeliveryDraftRepository drafts,
            CustomerDeliveryBillingService billing, CustomerDeliveryBillRepository bills, EmployeeTargetClosureLookup closures,
            EmployeeTargetSettlementService employees, ProcessingTargetCustomer customers, FinanceRepository finance,
            OperatorContextResolver actors, PermissionGuard permissions) {
        this.deliveries=deliveries; this.business=business; this.drafts=drafts; this.billing=billing; this.bills=bills;
        this.closures=closures; this.employees=employees; this.customers=customers; this.finance=finance; this.actors=actors; this.permissions=permissions;
    }
    @GetMapping("/delivery-bills/{id}") public ApiResponse<BillContext> context(@PathVariable String id,HttpServletRequest request) {
        var actor=actor(request,PermissionCode.FINANCE_CUSTOMER_READ); var draft=deliveries.require(actor,id);
        var initial=bills.findInitial(id).orElse(null);
        var versions=initial==null ? List.<CustomerDeliveryBill>of() : bills.listByRoot(initial.rootBillId());
        var latest=versions.stream().max(java.util.Comparator.comparingInt(CustomerDeliveryBill::version)).orElse(initial);
        return ApiResponse.ok(new BillContext(draft,drafts.lines(id),drafts.confirmedSources(id), latest, latest==null ? List.of() : bills.lines(latest.billId())));
    }
    @PostMapping("/delivery-bills/{id}/confirmations") public ApiResponse<CustomerDeliveryBill> confirm(@PathVariable String id,@RequestBody Bill body,HttpServletRequest request) {
        var actor=actor(request,PermissionCode.FINANCE_BILLING_POST); deliveries.require(actor,id);
        return ApiResponse.ok(billing.confirm(actor,body.requestId(),id,body.lines(),body.lossQuantity(),body.coveredAmount(),body.coveredReceivableCode()));
    }
    @PostMapping("/delivery-bills/{id}/adjustments") public ApiResponse<CustomerDeliveryBill> adjust(@PathVariable String id,@RequestBody Bill body,HttpServletRequest request) {
        var actor=actor(request,PermissionCode.FINANCE_BILLING_POST); deliveries.require(actor,id);
        return ApiResponse.ok(billing.adjust(actor,body.requestId(),id,body.lines(),body.lossQuantity()));
    }
    @GetMapping("/employee-targets/{target}") public ApiResponse<EmployeeContext> employee(@PathVariable String target,HttpServletRequest request) {
        var actor=actor(request,PermissionCode.FINANCE_PAYROLL); business.assertCanRead(actor,target);
        return ApiResponse.ok(new EmployeeContext(closures.find(target).orElseThrow(() -> new BusinessException("加工目标不存在")),
                finance.listPayrollLines().stream().filter(row -> row.sourceKey()!=null && row.sourceKey().startsWith("employee-target:"+target+":")).toList()));
    }
    @PostMapping("/employee-targets/{target}/settlements") public ApiResponse<List<PayrollLine>> settle(@PathVariable String target,@RequestBody Employee body,HttpServletRequest request) {
        var actor=actor(request,PermissionCode.FINANCE_PAYROLL); business.assertCanRead(actor,target);
        var facts=customers.facts(BusinessTargetReference.parse(target)).orElseThrow(() -> new BusinessException("加工目标不存在"));
        if (facts.customerId()==null) throw new BusinessException("加工目标缺少真实客户");
        return ApiResponse.ok(employees.settle(actor,body.requestId(),target,facts.customerId(),body.shares()));
    }
    private OperatorContext actor(HttpServletRequest request,PermissionCode permission) { var actor=actors.requireSession(request); permissions.assertAllowed(permission,actor); return actor; }
    public record Bill(String requestId,List<CustomerChargeLine> lines,BigDecimal lossQuantity,BigDecimal coveredAmount,String coveredReceivableCode) {}
    public record Employee(String requestId,List<EmployeeWorkShare> shares) {}
    public record EmployeeContext(EmployeeTargetClosure closure,List<PayrollLine> payroll) {}
    public record BillContext(DeliveryDraft draft,List<DeliveryDraftLine> lines,List<DeliveryConfirmedSource> sources,CustomerDeliveryBill bill,List<CustomerDeliveryBillLine> billLines) {}
}
