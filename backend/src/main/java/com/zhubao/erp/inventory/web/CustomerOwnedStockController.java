package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.inventory.app.CustomerOwnedStockQuery;
import com.zhubao.erp.inventory.domain.CustomerOwnedStockView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户自有库存只读。客户编号来自登录绑定，查询不写出库或改库存。
 */
@RestController
@RequestMapping("/api/customer-portal/owned-stock")
public class CustomerOwnedStockController {

    private final CustomerOwnedStockQuery query;
    private final OperatorContextResolver operators;

    public CustomerOwnedStockController(CustomerOwnedStockQuery query, OperatorContextResolver operators) {
        this.query = query;
        this.operators = operators;
    }

    @GetMapping
    public ApiResponse<CustomerOwnedStockView> owned(HttpServletRequest request) {
        return ApiResponse.ok(query.query(operators.requireSession(request)));
    }
}
