package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FixedAssetDepreciationContinuityService;
import com.zhubao.erp.finance.domain.FixedAssetDepreciationRun;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 延续月折旧。不接收停用或报废的生效月份。
 */
@RestController
@RequestMapping("/api/finance/depreciation-continuations")
public class FixedAssetDepreciationController {

    private final FixedAssetDepreciationContinuityService depreciation;
    private final OperatorContextResolver actors;

    public FixedAssetDepreciationController(FixedAssetDepreciationContinuityService depreciation, OperatorContextResolver actors) {
        this.depreciation = depreciation;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<FixedAssetDepreciationRun> run(@RequestBody RunRequest body, HttpServletRequest request) {
        return ApiResponse.ok(depreciation.run(
                body.requestId(), actors.resolve(request), body.yearMonth(), body.stopEffectiveMonth()));
    }

    public record RunRequest(String requestId, String yearMonth, String stopEffectiveMonth) {
    }
}
