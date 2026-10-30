package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.inventory.app.ConsumableIssueService;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialReturn;
import com.zhubao.erp.inventory.domain.ConsumableCostEvent;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 耗材领退和实际耗用入口。未选定计耗方式时由服务拒绝。 */
@RestController
@RequestMapping("/api/inventory/consumable-issues")
public class ConsumableIssueController {

    private final ConsumableIssueService issues;
    private final OperatorContextResolver actors;

    public ConsumableIssueController(ConsumableIssueService issues, OperatorContextResolver actors) {
        this.issues = issues;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<CompanyMaterialIssue> issue(@RequestBody IssueRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(issues.issue(
                body.requestId(), actor, body.itemId(), body.receiptId(), body.workshopCode(), body.quantity()));
    }

    @PostMapping("/{issueId}/returns")
    public ApiResponse<CompanyMaterialReturn> returnIssued(@PathVariable String issueId, @RequestBody QuantityRequest body,
                                                           HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(issues.returnIssued(body.requestId(), actor, issueId, body.version(), body.quantity()));
    }

    @PostMapping("/{issueId}/consumptions")
    public ApiResponse<ConsumableCostEvent> consume(@PathVariable String issueId, @RequestBody QuantityRequest body,
                                                    HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(issues.consume(body.requestId(), actor, issueId, body.version(), body.quantity()));
    }

    public record IssueRequest(String requestId, String itemId, String receiptId, String workshopCode, BigDecimal quantity) {
    }

    public record QuantityRequest(String requestId, int version, BigDecimal quantity) {
    }
}
