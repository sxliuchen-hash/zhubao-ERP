package com.zhubao.erp.label.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.app.PrintJobService;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.security.app.OperatorContextResolver;
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

    public PrintJobController(PrintJobService printJobService, OperatorContextResolver operatorContextResolver) {
        this.printJobService = printJobService;
        this.operatorContextResolver = operatorContextResolver;
    }

    @PostMapping("/print-jobs")
    public ApiResponse<PrintJob> create(@RequestBody CreatePrintJobRequest body, HttpServletRequest request) {
        require(body);
        return ApiResponse.ok(printJobService.create(
                body.requestId(), body.businessCode(), body.templateCode(), body.copies(),
                operatorContextResolver.requireSession(request).operator()
        ));
    }

    @GetMapping("/print-jobs/{id}")
    public ApiResponse<PrintJob> get(@PathVariable String id, HttpServletRequest request) {
        operatorContextResolver.requireSession(request);
        return ApiResponse.ok(printJobService.get(id));
    }

    @GetMapping("/print-jobs")
    public ApiResponse<List<PrintJob>> list(HttpServletRequest request) {
        operatorContextResolver.requireSession(request);
        return ApiResponse.ok(printJobService.list());
    }

    @PostMapping("/print-jobs/{id}/retries")
    public ApiResponse<PrintJob> retry(
            @PathVariable String id,
            @RequestBody RetryPrintJobRequest body,
            HttpServletRequest request
    ) {
        require(body);
        return ApiResponse.ok(printJobService.retry(id, body.requestId(), body.reason(),
                operatorContextResolver.requireSession(request).operator()));
    }

    @PostMapping("/print-jobs/{id}/paper-checks")
    public ApiResponse<PrintJob> paper(
            @PathVariable String id,
            @RequestBody RetryPrintJobRequest body,
            HttpServletRequest request
    ) {
        require(body);
        return ApiResponse.ok(printJobService.verifyPaper(id, body.requestId(), body.reason(),
                operatorContextResolver.requireSession(request).operator()));
    }

    @PostMapping("/print-jobs/{id}/relabels")
    public ApiResponse<PrintJob> relabel(
            @PathVariable String id,
            @RequestBody RetryPrintJobRequest body,
            HttpServletRequest request
    ) {
        require(body);
        return ApiResponse.ok(printJobService.relabel(id, body.requestId(), body.reason(),
                operatorContextResolver.requireSession(request).operator()));
    }

    @PostMapping("/agents/jobs/claim")
    public ApiResponse<PrintJob> claim(
            @RequestHeader("X-Print-Station") String stationId,
            @RequestHeader("X-Print-Token") String token
    ) {
        return ApiResponse.ok(printJobService.claim(stationId, token));
    }

    @PostMapping("/agents/jobs/{id}/report")
    public ApiResponse<PrintJob> report(
            @PathVariable String id,
            @RequestHeader("X-Print-Station") String stationId,
            @RequestHeader("X-Print-Token") String token,
            @RequestBody ReportPrintJobRequest body
    ) {
        require(body);
        return ApiResponse.ok(printJobService.report(id, stationId, token, body.outcome(), body.failureReason()));
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
