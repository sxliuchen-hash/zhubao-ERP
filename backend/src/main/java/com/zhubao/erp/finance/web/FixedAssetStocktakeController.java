package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FixedAssetStocktakeService;
import com.zhubao.erp.finance.domain.FixedAssetStocktake;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产逐件盘点。确认差异的权限与登记实盘分开。
 */
@RestController
@RequestMapping("/api/finance/fixed-asset-stocktakes")
public class FixedAssetStocktakeController {

    private final FixedAssetStocktakeService stocktakes;
    private final OperatorContextResolver actors;

    public FixedAssetStocktakeController(FixedAssetStocktakeService stocktakes, OperatorContextResolver actors) {
        this.stocktakes = stocktakes;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<FixedAssetStocktake> open(@RequestBody OpenRequest body, HttpServletRequest request) {
        return ApiResponse.ok(stocktakes.open(body.requestId(), actors.resolve(request), body.assetCode(), body.countedAt()));
    }

    @PostMapping("/counts")
    public ApiResponse<FixedAssetStocktake> count(@RequestBody CountRequest body, HttpServletRequest request) {
        return ApiResponse.ok(stocktakes.count(
                body.requestId(), actors.resolve(request), body.stocktakeId(), body.expectedVersion(),
                body.seen(), body.location(), body.custodian(), body.exceptionText()));
    }

    @PostMapping("/confirmations")
    public ApiResponse<FixedAssetStocktake> confirm(@RequestBody ConfirmRequest body, HttpServletRequest request) {
        return ApiResponse.ok(stocktakes.confirm(
                body.requestId(), actors.resolve(request), body.stocktakeId(), body.expectedVersion()));
    }

    public record OpenRequest(String requestId, String assetCode, String countedAt) {
    }

    public record CountRequest(String requestId, String stocktakeId, Long expectedVersion, String seen,
                               String location, String custodian, String exceptionText) {
    }

    public record ConfirmRequest(String requestId, String stocktakeId, Long expectedVersion) {
    }
}
