package com.zhubao.erp.label.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.app.PrintJobService;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/label")
public class PrintJobController {

    private final PrintJobService printJobService;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessionService;

    public PrintJobController(
            PrintJobService printJobService,
            OperatorContextResolver operatorContextResolver,
            AuthSessionService authSessionService
    ) {
        this.printJobService = printJobService;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/print-jobs")
    public ApiResponse<PrintJob> create(@RequestBody CreatePrintJobRequest body, HttpServletRequest request) {
        throw new BusinessException("旧版打印入口已停止接单，请升级客户端并选择工位及队列");
    }

    @GetMapping("/print-jobs/{id}")
    public ApiResponse<PrintJob> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(printJobService.get(id, account(request)));
    }

    @GetMapping("/print-jobs")
    public ApiResponse<List<PrintJob>> list(HttpServletRequest request) {
        return ApiResponse.ok(printJobService.list(account(request)));
    }

    @PostMapping("/print-jobs/{id}/retries")
    public ApiResponse<PrintJob> retry(
            @PathVariable String id,
            @RequestBody RetryPrintJobRequest body,
            HttpServletRequest request
    ) {
        throw new BusinessException("旧版补打入口已停止接单，请核纸后使用新打印批次");
    }

    @PostMapping("/print-jobs/{id}/paper-checks")
    public ApiResponse<PrintJob> paper(
            @PathVariable String id,
            @RequestBody RetryPrintJobRequest body,
            HttpServletRequest request
    ) {
        require(body);
        AuthUserAccount account = account(request);
        printJobService.get(id, account);
        return ApiResponse.ok(printJobService.verifyPaper(id, body.requestId(), body.reason(), account.operator()));
    }

    @PostMapping("/print-jobs/{id}/relabels")
    public ApiResponse<PrintJob> relabel(
            @PathVariable String id,
            @RequestBody RetryPrintJobRequest body,
            HttpServletRequest request
    ) {
        require(body);
        AuthUserAccount account = account(request);
        printJobService.get(id, account);
        return ApiResponse.ok(printJobService.relabel(id, body.requestId(), body.reason(), account.operator()));
    }

    @PostMapping("/agents/jobs/claim")
    public ApiResponse<PrintJob> claim(
            @RequestHeader("X-Print-Station") String stationId,
            @RequestHeader("X-Print-Token") String token
    ) {
        throw new BusinessException("旧版工位领取入口已停用，请升级厂内打印程序");
    }

    @PostMapping("/agents/jobs/{id}/report")
    public ApiResponse<PrintJob> report(
            @PathVariable String id,
            @RequestHeader("X-Print-Station") String stationId,
            @RequestHeader("X-Print-Token") String token,
            @RequestBody ReportPrintJobRequest body
    ) {
        throw new BusinessException("旧版工位回报入口已停用，请升级厂内打印程序");
    }

    private AuthUserAccount account(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        AuthUserAccount account = authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再打印"));
        return new AuthUserAccount(
                account.username(), account.passwordHash(), account.operator(), account.role(), account.dataScope(),
                account.enabled(), account.demoAccount(), account.passwordChangedAt(), account.failedLoginCount(),
                account.lockedUntil(), context.customerScope(), account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(), account.allowedProcesses()
        );
    }

    private void require(Object body) {
        if (body == null) {
            throw new BusinessException("打印任务不能为空");
        }
    }

    public record CreatePrintJobRequest(String requestId, String businessCode, String templateCode, int copies) {
    }

    public record RetryPrintJobRequest(String requestId, String reason) {
    }

    public record ReportPrintJobRequest(String outcome, String failureReason) {
    }
}
