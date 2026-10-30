package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.inventory.app.CompanyMaterialIssueService;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialReturn;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 公司料领用和退回。按原批次办理，不把领用记成消耗或客户来料。
 */
@RestController
@RequestMapping("/api/inventory/company-material-issues")
public class CompanyMaterialIssueController {

    private final CompanyMaterialIssueService issues;
    private final OperatorContextResolver actors;

    public CompanyMaterialIssueController(CompanyMaterialIssueService issues, OperatorContextResolver actors) {
        this.issues = issues;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<CompanyMaterialIssue> issue(@RequestBody IssueRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(issues.issue(
                body.requestId(), actor, body.itemId(), body.batchCode(), body.fromWarehouse(), body.workshopCode(),
                body.quantity(), body.weight(), body.unit(), body.targetCode()));
    }

    @PostMapping("/{issueId}/returns")
    public ApiResponse<CompanyMaterialReturn> returnIssued(@PathVariable String issueId, @RequestBody ReturnRequest body,
                                                           HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(issues.returnIssued(
                body.requestId(), actor, issueId, body.version(), body.quantity(), body.weight(), body.unit()));
    }

    public record IssueRequest(
            String requestId,
            String itemId,
            String batchCode,
            String fromWarehouse,
            String workshopCode,
            BigDecimal quantity,
            BigDecimal weight,
            String unit,
            String targetCode
    ) {
    }

    public record ReturnRequest(
            String requestId,
            int version,
            BigDecimal quantity,
            BigDecimal weight,
            String unit
    ) {
    }
}
