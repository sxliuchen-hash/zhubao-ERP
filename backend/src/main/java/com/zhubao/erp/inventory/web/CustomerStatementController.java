package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.CustomerStatementCutoffQuery;
import com.zhubao.erp.inventory.domain.CustomerStatementCutoff;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * 客户截止日对账。只读数量，确认动作不会出库或出账。
 */
@RestController
@RequestMapping("/api/customer-portal/statements")
public class CustomerStatementController {

    private final CustomerStatementCutoffQuery query;
    private final OperatorContextResolver operators;

    public CustomerStatementController(CustomerStatementCutoffQuery query, OperatorContextResolver operators) {
        this.query = query;
        this.operators = operators;
    }

    @GetMapping
    public ApiResponse<CustomerStatementCutoff> statement(@RequestParam(required = false) String cutoff,
                                                          HttpServletRequest request) {
        return ApiResponse.ok(query.statement(operators.requireSession(request), parse(cutoff)));
    }

    @PostMapping
    public ApiResponse<Void> acknowledge(@RequestParam(required = false) String cutoff, HttpServletRequest request) {
        query.acknowledge(operators.requireSession(request), parse(cutoff));
        return ApiResponse.ok();
    }

    private static Instant parse(String cutoff) {
        if (cutoff == null || cutoff.isBlank()) {
            throw new BusinessException("请指定截止日");
        }
        try {
            return Instant.parse(cutoff.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("请指定截止日");
        }
    }
}
