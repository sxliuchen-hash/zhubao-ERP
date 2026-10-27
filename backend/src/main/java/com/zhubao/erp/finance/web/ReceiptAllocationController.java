package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FinanceBillingService;
import com.zhubao.erp.finance.app.FinanceReadAuthorizer;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.FinanceCommands.ReceiptAllocationCommand;
import com.zhubao.erp.finance.domain.ReceiptAllocationOperation;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 已有预收的后续核销和撤销。客户范围在回放之前检查。 */
@RestController
@RequestMapping("/api/finance/receipts")
public class ReceiptAllocationController {

    private final FinanceBillingService billingService;
    private final FinanceReadAuthorizer authorizer;
    private final OperatorContextResolver operators;

    public ReceiptAllocationController(
            FinanceBillingService billingService,
            FinanceReadAuthorizer authorizer,
            OperatorContextResolver operators
    ) {
        this.billingService = billingService;
        this.authorizer = authorizer;
        this.operators = operators;
    }

    @PostMapping("/{code}/allocation-operations")
    public ApiResponse<ReceiptAllocationOperation> allocate(
            @PathVariable String code,
            @RequestBody AllocationRequest body,
            HttpServletRequest request
    ) {
        OperatorContext actor = operators.resolve(request);
        authorizer.assertSignedIn(request, actor);
        CustomerReceipt receipt = billingService.receipt(code);
        authorizer.assertCustomerWrite(actor, receipt.customerId());
        return ApiResponse.ok(billingService.allocateExisting(
                code, body.requestId(), body.action(), body.reason(), body.lines(), actor.operator()
        ));
    }

    public record AllocationRequest(String requestId, String action, String reason, List<ReceiptAllocationCommand> lines) {
    }
}
