package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FixedAssetAcquisitionService;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionView;
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
 * 固定资产购置来源。不把付款当成原值，也不把没有凭据的记录标成已付。
 */
@RestController
@RequestMapping("/api/finance/fixed-asset-acquisitions")
public class FixedAssetAcquisitionController {

    private final FixedAssetAcquisitionService acquisitions;
    private final OperatorContextResolver actors;

    public FixedAssetAcquisitionController(FixedAssetAcquisitionService acquisitions, OperatorContextResolver actors) {
        this.acquisitions = acquisitions;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<FixedAssetAcquisitionView>> list(
            @RequestParam(required = false) String assetCode, HttpServletRequest request) {
        return ApiResponse.ok(acquisitions.list(actors.resolve(request), assetCode));
    }

    @PostMapping
    public ApiResponse<FixedAssetAcquisitionView> receive(@RequestBody ReceiveRequest body, HttpServletRequest request) {
        return ApiResponse.ok(acquisitions.receive(
                body.requestId(),
                actors.resolve(request),
                body.source(),
                body.assetCode(),
                body.name(),
                body.category(),
                body.originalValue(),
                body.residualValue(),
                body.startMonth(),
                body.usefulMonths(),
                body.supplierName(),
                body.acquiredOn(),
                body.handoverLocation(),
                body.handoverReceiver(),
                body.coveredPayableCode(),
                body.paymentAmount(),
                body.paymentDate(),
                body.paymentVoucher()
        ));
    }

    public record ReceiveRequest(
            String requestId,
            String source,
            String assetCode,
            String name,
            String category,
            BigDecimal originalValue,
            BigDecimal residualValue,
            String startMonth,
            Integer usefulMonths,
            String supplierName,
            String acquiredOn,
            String handoverLocation,
            String handoverReceiver,
            String coveredPayableCode,
            BigDecimal paymentAmount,
            String paymentDate,
            String paymentVoucher
    ) {
    }
}
