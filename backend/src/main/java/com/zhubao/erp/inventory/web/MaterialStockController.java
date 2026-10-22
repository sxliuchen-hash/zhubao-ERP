package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.MaterialStockService;
import com.zhubao.erp.inventory.app.StockDesk;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.zhubao.erp.inventory.domain.StockDocument;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/material-stock")
public class MaterialStockController {

    private final MaterialStockService materialStockService;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessionService;

    public MaterialStockController(MaterialStockService materialStockService, OperatorContextResolver operatorContextResolver,
                                   AuthSessionService authSessionService) {
        this.materialStockService = materialStockService;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessionService = authSessionService;
    }

    @GetMapping("/overview")
    public ApiResponse<StockDesk.Overview> overview(@RequestParam String scope,
                                                     @RequestParam(required = false) Long customerId,
                                                     @RequestParam(required = false) String materialCode,
                                                     @RequestParam(required = false) String batchCode,
                                                     @RequestParam(required = false) String warehouseCode,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String fromDate,
                                                     @RequestParam(required = false) String toDate,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.overview(query(scope, customerId, materialCode, batchCode, warehouseCode, status, fromDate, toDate, "", ""), account(request)));
    }

    @GetMapping("/batches")
    public ApiResponse<List<StockDesk.BatchCard>> batches(@RequestParam String scope,
                                                           @RequestParam(required = false) Long customerId,
                                                           @RequestParam(required = false) String materialCode,
                                                           @RequestParam(required = false) String batchCode,
                                                           @RequestParam(required = false) String warehouseCode,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String fromDate,
                                                           @RequestParam(required = false) String toDate,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.cards(query(scope, customerId, materialCode, batchCode, warehouseCode, status, fromDate, toDate, "", ""), account(request)));
    }

    @GetMapping("/movements")
    public ApiResponse<List<StockDesk.MovementRow>> movements(@RequestParam String scope,
                                                               @RequestParam(required = false) String batchCode,
                                                               @RequestParam(required = false) String motherOrderCode,
                                                               @RequestParam(required = false) String documentCode,
                                                               HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.movements(query(scope, null, "", batchCode, "", "", "", "", motherOrderCode, documentCode), account(request)));
    }

    @PostMapping("/company-receipts")
    public ApiResponse<String> receiveCompany(@RequestBody StockDesk.ReceiptInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.receiveCompany(require(body), account(request)));
    }

    @GetMapping("/documents/{documentCode}")
    public ApiResponse<StockDocument> document(@PathVariable String documentCode, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.document(documentCode, account(request)));
    }

    @PostMapping("/company-issues")
    public ApiResponse<String> issueCompany(@RequestBody StockDesk.IssueInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.issueCompany(require(body), account(request)));
    }

    @PostMapping("/company-returns")
    public ApiResponse<String> returnCompany(@RequestBody StockDesk.ReturnInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.returnCompany(require(body), account(request)));
    }

    @PostMapping("/prep-receipts")
    public ApiResponse<String> receivePrep(@RequestBody StockDesk.ReceiptInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.receivePrep(require(body), account(request)));
    }

    @PostMapping("/prep-confirmations")
    public ApiResponse<String> confirm(@RequestBody StockDesk.ConfirmInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.confirmReceipt(require(body), account(request)));
    }

    @PostMapping("/prep-issues")
    public ApiResponse<String> issuePrep(@RequestBody StockDesk.IssueInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.issuePrep(require(body), account(request)));
    }

    @PostMapping("/prep-returns")
    public ApiResponse<String> returnPrep(@RequestBody StockDesk.ReturnInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.returnPrep(require(body), account(request)));
    }

    @PostMapping("/customer-returns")
    public ApiResponse<String> returnCustomer(@RequestBody StockDesk.CustomerReturnInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.returnToCustomer(require(body), account(request)));
    }

    @PostMapping("/prep-orders")
    public ApiResponse<String> openOrder(@RequestBody StockDesk.OpenOrderInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.openOrder(require(body), account(request)));
    }

    @PostMapping("/freezes")
    public ApiResponse<String> freeze(@RequestBody StockDesk.HoldInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.freeze(require(body), account(request)));
    }

    @PostMapping("/unfreezes")
    public ApiResponse<String> unfreeze(@RequestBody StockDesk.HoldInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.unfreeze(require(body), account(request)));
    }

    @PostMapping("/corrections")
    public ApiResponse<String> correct(@RequestBody StockDesk.CorrectionInput body, HttpServletRequest request) {
        return ApiResponse.ok(materialStockService.correct(require(body), account(request)));
    }

    private StockDesk.Query query(String scope, Long customerId, String materialCode, String batchCode, String warehouseCode,
                                  String status, String fromDate, String toDate, String motherOrderCode, String documentCode) {
        return new StockDesk.Query(scope, customerId, materialCode, batchCode, warehouseCode, status, fromDate, toDate, motherOrderCode, documentCode);
    }

    private <T> T require(T body) {
        if (body == null) {
            throw new BusinessException("库存操作必须带请求编号");
        }
        return body;
    }

    private AuthUserAccount account(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        AuthUserAccount account = authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再操作库存"));
        return new AuthUserAccount(
                account.username(), account.passwordHash(), account.operator(), account.role(), account.dataScope(),
                account.enabled(), account.demoAccount(), account.passwordChangedAt(), account.failedLoginCount(),
                account.lockedUntil(), context.customerScope(), account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(), account.allowedProcesses(), account.coverDuties(), account.teamCode(), account.departmentCode()
        );
    }
}
