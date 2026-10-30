package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.inventory.app.MaterialStocktakeService;
import com.zhubao.erp.inventory.domain.MaterialStocktake;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 盘点入口。确认只追加差额，查询和填写实盘不改原账面。 */
@RestController
@RequestMapping("/api/inventory/stocktakes")
public class MaterialStocktakeController {

    private final MaterialStocktakeService stocktakes;
    private final OperatorContextResolver actors;

    public MaterialStocktakeController(MaterialStocktakeService stocktakes, OperatorContextResolver actors) {
        this.stocktakes = stocktakes;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<MaterialStocktake> open(@RequestBody OpenRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(stocktakes.open(
                body.requestId(), actor, body.itemId(), body.batchCode(), body.locationCode(), body.countedAt(), body.tool()));
    }

    @PostMapping("/{stocktakeId}/counts")
    public ApiResponse<MaterialStocktake> count(@PathVariable String stocktakeId, @RequestBody CountRequest body,
                                                HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(stocktakes.count(body.requestId(), actor, stocktakeId, body.version(), body.countedQuantity()));
    }

    @PostMapping("/{stocktakeId}/confirmations")
    public ApiResponse<MaterialStocktake> confirm(@PathVariable String stocktakeId, @RequestBody ConfirmRequest body,
                                                  HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(stocktakes.confirm(body.requestId(), actor, stocktakeId, body.version()));
    }

    public record OpenRequest(String requestId, String itemId, String batchCode, String locationCode, String countedAt, boolean tool) {
    }

    public record CountRequest(String requestId, int version, BigDecimal countedQuantity) {
    }

    public record ConfirmRequest(String requestId, int version) {
    }
}
