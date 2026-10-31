package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FixedAssetDispositionService;
import com.zhubao.erp.finance.domain.FixedAssetDispositionView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 记录资产停用、报废或处置。不删除原卡，也不把报废当成收款。
 */
@RestController
@RequestMapping("/api/finance/fixed-asset-dispositions")
public class FixedAssetDispositionController {

    private final FixedAssetDispositionService dispositions;
    private final OperatorContextResolver actors;

    public FixedAssetDispositionController(FixedAssetDispositionService dispositions, OperatorContextResolver actors) {
        this.dispositions = dispositions;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<FixedAssetDispositionView>> list(@RequestParam String assetCode, HttpServletRequest request) {
        return ApiResponse.ok(dispositions.list(actors.resolve(request), assetCode));
    }

    @PostMapping
    public ApiResponse<FixedAssetDispositionView> record(@RequestBody RecordRequest body, HttpServletRequest request) {
        return ApiResponse.ok(dispositions.record(
                body.requestId(), actors.resolve(request), body.assetCode(), body.kind(),
                body.occurredOn(), body.basis(), body.receiptCode(), body.proceeds()));
    }

    public record RecordRequest(
            String requestId,
            String assetCode,
            String kind,
            String occurredOn,
            String basis,
            String receiptCode,
            BigDecimal proceeds
    ) {
    }
}
