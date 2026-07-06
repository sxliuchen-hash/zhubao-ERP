package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final StockLedgerService stockLedgerService;

    public InventoryController(StockLedgerService stockLedgerService) {
        this.stockLedgerService = stockLedgerService;
    }

    @PostMapping("/ledger/transfers")
    public ApiResponse<StockLedgerEntry> transfer(@Valid @RequestBody StockTransferCommand command) {
        return ApiResponse.ok(stockLedgerService.transfer(command));
    }

    @GetMapping("/ledgers")
    public ApiResponse<List<StockLedgerEntry>> ledgers() {
        return ApiResponse.ok(stockLedgerService.ledgers());
    }

    @GetMapping("/balances")
    public ApiResponse<List<StockBalance>> balances() {
        return ApiResponse.ok(stockLedgerService.balances());
    }
}
