package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FixedAssetCustodyService;
import com.zhubao.erp.finance.domain.FixedAssetCustodyView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资产位置和保管交接。只追加历史，不接收原值。
 */
@RestController
@RequestMapping("/api/finance/fixed-asset-custody-transfers")
public class FixedAssetCustodyController {

    private final FixedAssetCustodyService custody;
    private final OperatorContextResolver actors;

    public FixedAssetCustodyController(FixedAssetCustodyService custody, OperatorContextResolver actors) {
        this.custody = custody;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<FixedAssetCustodyView>> history(
            @RequestParam String assetCode, HttpServletRequest request) {
        return ApiResponse.ok(custody.history(actors.resolve(request), assetCode));
    }

    @PostMapping
    public ApiResponse<FixedAssetCustodyView> transfer(@RequestBody TransferRequest body, HttpServletRequest request) {
        return ApiResponse.ok(custody.transfer(
                body.requestId(),
                actors.resolve(request),
                body.assetCode(),
                body.expectedVersion(),
                body.location(),
                body.custodian(),
                body.useStatus()
        ));
    }

    public record TransferRequest(
            String requestId,
            String assetCode,
            Long expectedVersion,
            String location,
            String custodian,
            String useStatus
    ) {
    }
}
