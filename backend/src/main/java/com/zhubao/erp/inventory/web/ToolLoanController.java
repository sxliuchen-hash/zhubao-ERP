package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.inventory.app.ToolCustodyService;
import com.zhubao.erp.inventory.domain.ToolDisposition;
import com.zhubao.erp.inventory.domain.ToolLoan;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 工具借还入口。同名人员按持有人编号区分。 */
@RestController
@RequestMapping("/api/inventory/tool-loans")
public class ToolLoanController {

    private final ToolCustodyService tools;
    private final OperatorContextResolver actors;

    public ToolLoanController(ToolCustodyService tools, OperatorContextResolver actors) {
        this.tools = tools;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<ToolLoan> borrow(@RequestBody BorrowRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(tools.borrow(
                body.requestId(), actor, body.itemId(), body.warehouseCode(), body.holderId(), body.holderName(),
                body.quantity(), body.pieceCode()));
    }

    @PostMapping("/{loanId}/returns")
    public ApiResponse<ToolLoan> giveBack(@PathVariable String loanId, @RequestBody ReturnRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(tools.giveBack(body.requestId(), actor, loanId, body.version(), body.quantity()));
    }

    @PostMapping("/{loanId}/scraps")
    public ApiResponse<ToolDisposition> scrap(@PathVariable String loanId, @RequestBody ScrapRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(tools.scrap(body.requestId(), actor, loanId, body.version(), body.quantity(), body.reason()));
    }

    public record BorrowRequest(
            String requestId, String itemId, String warehouseCode, String holderId, String holderName,
            BigDecimal quantity, String pieceCode) {
    }

    public record ReturnRequest(String requestId, int version, BigDecimal quantity) {
    }

    public record ScrapRequest(String requestId, int version, BigDecimal quantity, String reason) {
    }
}
