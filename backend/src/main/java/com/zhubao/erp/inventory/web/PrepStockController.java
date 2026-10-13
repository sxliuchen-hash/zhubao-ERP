package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.PrepStockService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/prep-stock")
public class PrepStockController {

    private final PrepStockService prepStockService;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessionService;

    public PrepStockController(
            PrepStockService prepStockService,
            OperatorContextResolver operatorContextResolver,
            AuthSessionService authSessionService
    ) {
        this.prepStockService = prepStockService;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessionService = authSessionService;
    }

    @GetMapping("/customer")
    public ApiResponse<List<StockBalance>> customer(HttpServletRequest request) {
        return ApiResponse.ok(prepStockService.customerPrep(account(request)));
    }

    @GetMapping("/factory")
    public ApiResponse<List<StockBalance>> factory(HttpServletRequest request) {
        return ApiResponse.ok(prepStockService.factoryStock(account(request)));
    }

    @GetMapping("/company")
    public ApiResponse<List<StockBalance>> company(HttpServletRequest request) {
        return ApiResponse.ok(prepStockService.companyStock(account(request)));
    }

    @PostMapping("/customer-receipts")
    public ApiResponse<String> receive(@RequestBody PrepReceiptRequest body, HttpServletRequest request) {
        requireBody(body);
        prepStockService.receiveCustomerPrep(body.customerId(), body.materialCode(), body.warehouseCode(),
                body.quantity(), body.weight(), body.batchCode(), body.requestId(), account(request));
        return ApiResponse.ok("已登记客户备料");
    }

    @PostMapping("/issues")
    public ApiResponse<String> issue(@RequestBody PrepIssueRequest body, HttpServletRequest request) {
        requireBody(body);
        prepStockService.issueToMother(body.customerId(), body.materialCode(), body.warehouseCode(), body.batchCode(),
                body.motherOrderCode(), body.quantity(), body.weight(), body.requestId(), account(request));
        return ApiResponse.ok("已领用到加工单");
    }

    @PostMapping("/company-receipts")
    public ApiResponse<String> receiveCompany(@RequestBody CompanyReceiptRequest body, HttpServletRequest request) {
        requireBody(body);
        prepStockService.receiveCompany(body.materialCode(), body.warehouseCode(), body.quantity(), body.weight(),
                body.batchCode(), body.requestId(), account(request));
        return ApiResponse.ok("已登记公司自有生产物料");
    }

    private void requireBody(Object body) {
        if (body == null) {
            throw new BusinessException("库存操作必须带请求编号");
        }
    }

    private AuthUserAccount account(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        AuthUserAccount account = authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再操作库存"));
        return new AuthUserAccount(
                account.username(), account.passwordHash(), account.operator(), account.role(), account.dataScope(),
                account.enabled(), account.demoAccount(), account.passwordChangedAt(), account.failedLoginCount(),
                account.lockedUntil(), context.customerScope(), account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(), account.allowedProcesses()
        );
    }

    public record PrepReceiptRequest(Long customerId, String materialCode, String warehouseCode,
                                     BigDecimal quantity, BigDecimal weight, String batchCode, String requestId) {
    }

    public record PrepIssueRequest(Long customerId, String materialCode, String warehouseCode, String batchCode,
                                   String motherOrderCode, BigDecimal quantity, BigDecimal weight, String requestId) {
    }

    public record CompanyReceiptRequest(String materialCode, String warehouseCode, BigDecimal quantity,
                                        BigDecimal weight, String batchCode, String requestId) {
    }
}
