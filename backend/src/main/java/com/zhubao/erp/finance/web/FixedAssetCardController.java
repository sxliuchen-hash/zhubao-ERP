package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FixedAssetCardAdoptionService;
import com.zhubao.erp.finance.domain.FixedAssetCardView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 原固定资产卡片的实物字段接续。不接收原值，也不新建资产编码。
 */
@RestController
@RequestMapping("/api/finance/fixed-asset-cards")
public class FixedAssetCardController {

    private final FixedAssetCardAdoptionService cards;
    private final OperatorContextResolver actors;

    public FixedAssetCardController(FixedAssetCardAdoptionService cards, OperatorContextResolver actors) {
        this.cards = cards;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<FixedAssetCardView>> list(HttpServletRequest request) {
        return ApiResponse.ok(cards.cards(actor(request)));
    }

    @PostMapping("/supplements")
    public ApiResponse<FixedAssetCardView> supplement(@RequestBody SupplementRequest body, HttpServletRequest request) {
        return ApiResponse.ok(cards.supplement(
                body.requestId(),
                actor(request),
                body.assetCode(),
                body.expectedVersion(),
                body.model(),
                body.serialNumber(),
                body.location(),
                body.custodian(),
                body.useStatus()
        ));
    }

    private OperatorContext actor(HttpServletRequest request) {
        return actors.resolve(request);
    }

    public record SupplementRequest(
            String requestId,
            String assetCode,
            Long expectedVersion,
            String model,
            String serialNumber,
            String location,
            String custodian,
            String useStatus
    ) {
    }
}
