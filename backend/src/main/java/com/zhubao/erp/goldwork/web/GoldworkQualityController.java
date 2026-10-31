package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkQualityService;
import com.zhubao.erp.goldwork.domain.GoldworkQualityView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 金工份额质量。不改成色和收费。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/qualities")
public class GoldworkQualityController {

    private final GoldworkQualityService qualities;
    private final OperatorContextResolver actors;

    public GoldworkQualityController(GoldworkQualityService qualities, OperatorContextResolver actors) {
        this.qualities = qualities;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkQualityView>> list(@PathVariable String goldworkCode, HttpServletRequest request) {
        return ApiResponse.ok(qualities.list(actors.resolve(request), goldworkCode));
    }

    @PostMapping
    public ApiResponse<GoldworkQualityView> decide(@PathVariable String goldworkCode, @RequestBody DecideRequest body,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(qualities.decide(
                body.requestId(), actors.resolve(request), goldworkCode, body.reportId(), body.priorQualityId(),
                body.outcome(), body.pieceCount(), body.netWeight(), body.weightUnit(), body.purity(), body.note(),
                body.useBeadDefault(), body.changePurity(), body.changeFee()));
    }

    public record DecideRequest(
            String requestId,
            String reportId,
            String priorQualityId,
            String outcome,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            String weightUnit,
            String purity,
            String note,
            boolean useBeadDefault,
            boolean changePurity,
            boolean changeFee
    ) {
    }
}
