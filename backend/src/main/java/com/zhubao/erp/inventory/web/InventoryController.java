package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import jakarta.servlet.http.HttpServletRequest;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final StockLedgerService stockLedgerService;
    private final OperatorContextResolver operatorContextResolver;
    private final BusinessDataAccess dataAccess;

    public InventoryController(StockLedgerService stockLedgerService) {
        this(stockLedgerService, null, null);
    }

    @Autowired
    public InventoryController(StockLedgerService stockLedgerService, OperatorContextResolver operatorContextResolver, BusinessDataAccess dataAccess) {
        this.stockLedgerService = stockLedgerService;
        this.operatorContextResolver = operatorContextResolver;
        this.dataAccess = dataAccess;
    }

    @GetMapping("/ledgers")
    public ApiResponse<List<StockLedgerEntry>> ledgers(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        return ApiResponse.ok(stockLedgerService.ledgers().stream()
                .filter(entry -> (entry.from() == null || dataAccess.canRead(context, entry.from()))
                        && (entry.to() == null || dataAccess.canRead(context, entry.to())))
                .toList());
    }

    @GetMapping("/balances")
    public ApiResponse<List<StockBalance>> balances(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        return ApiResponse.ok(stockLedgerService.balances().stream()
                .filter(balance -> dataAccess.canRead(context, balance.dimension())).toList());
    }
}
