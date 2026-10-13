package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.OrderChangeService;
import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.OperatorContext;
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

@RestController
@RequestMapping("/api/business/order-changes")
public class OrderChangeController {

    private final OrderChangeService orderChangeService;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessionService;

    public OrderChangeController(
            OrderChangeService orderChangeService,
            OperatorContextResolver operatorContextResolver,
            AuthSessionService authSessionService
    ) {
        this.orderChangeService = orderChangeService;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessionService = authSessionService;
    }

    @PostMapping
    public ApiResponse<OrderChange> draft(@RequestBody DraftRequest body, HttpServletRequest request) {
        require(body);
        return ApiResponse.ok(orderChangeService.draft(
                body.motherOrderCode(), body.workOrderCode(), body.newRequirement(), body.newDueDate(), body.requestId(),
                body.newRoute(), body.expectedVersion(), body.reworkProcess(), account(request)
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderChange> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(orderChangeService.present(id, account(request)));
    }

    @GetMapping
    public ApiResponse<List<OrderChange>> list(@RequestParam String motherOrderCode, HttpServletRequest request) {
        return ApiResponse.ok(orderChangeService.list(motherOrderCode, account(request)));
    }

    @PostMapping("/{id}/finance-submissions")
    public ApiResponse<OrderChange> submit(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(orderChangeService.submitFinance(id, account(request)));
    }

    @PostMapping("/{id}/finance-confirmations")
    public ApiResponse<OrderChange> confirm(@PathVariable String id, @RequestBody FinanceRequest body, HttpServletRequest request) {
        require(body);
        return ApiResponse.ok(orderChangeService.confirmFinance(
                id, body.amountUnchanged(), body.customerUnitPrice(), body.workerFee(),
                body.customerDelta(), body.workerDelta(), body.receivableCode(), body.processName(), body.workerName(),
                body.note(), account(request)
        ));
    }

    @PostMapping("/{id}/finance-rejections")
    public ApiResponse<OrderChange> reject(@PathVariable String id, @RequestBody FinanceRequest body, HttpServletRequest request) {
        require(body);
        return ApiResponse.ok(orderChangeService.rejectFinance(id, body.note(), account(request)));
    }

    @PostMapping("/{id}/applications")
    public ApiResponse<OrderChange> apply(@PathVariable String id, @RequestBody ApplyRequest body, HttpServletRequest request) {
        require(body);
        return ApiResponse.ok(orderChangeService.apply(id, body.requestId(), body.expectedVersion(), account(request)));
    }

    @GetMapping("/timeline")
    public ApiResponse<List<String>> timeline(@RequestParam String motherOrderCode, HttpServletRequest request) {
        return ApiResponse.ok(orderChangeService.timeline(motherOrderCode, account(request)));
    }

    private AuthUserAccount account(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        AuthUserAccount account = authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再改单"));
        return new AuthUserAccount(
                account.username(), account.passwordHash(), account.operator(), account.role(), account.dataScope(),
                account.enabled(), account.demoAccount(), account.passwordChangedAt(), account.failedLoginCount(),
                account.lockedUntil(), context.customerScope(), account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(), account.allowedProcesses()
        );
    }

    private void require(Object body) {
        if (body == null) {
            throw new BusinessException("改单请求不能为空");
        }
    }

    public record DraftRequest(
            String motherOrderCode,
            String workOrderCode,
            String newRequirement,
            String newDueDate,
            String requestId,
            String newRoute,
            Integer expectedVersion,
            String reworkProcess
    ) {
    }

    public record FinanceRequest(
            boolean amountUnchanged,
            BigDecimal customerUnitPrice,
            BigDecimal workerFee,
            BigDecimal customerDelta,
            BigDecimal workerDelta,
            String receivableCode,
            String processName,
            String workerName,
            String note
    ) {
    }

    public record ApplyRequest(String requestId, Integer expectedVersion) {
    }
}
