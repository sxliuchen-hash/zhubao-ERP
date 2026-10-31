package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkRecoveryService;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryView;
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

/** 回收去向。不并进成品，不折抵费用。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/recoveries")
public class GoldworkRecoveryController {

    private final GoldworkRecoveryService recoveries;
    private final OperatorContextResolver actors;

    public GoldworkRecoveryController(GoldworkRecoveryService recoveries, OperatorContextResolver actors) {
        this.recoveries = recoveries;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkRecoveryView>> list(@PathVariable String goldworkCode, HttpServletRequest request) {
        return ApiResponse.ok(recoveries.list(actors.resolve(request), goldworkCode));
    }

    @PostMapping
    public ApiResponse<GoldworkRecoveryView> record(@PathVariable String goldworkCode, @RequestBody RecordRequest body,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(recoveries.record(
                body.requestId(), actors.resolve(request), goldworkCode, body.sourceLineId(), body.outputMovementId(),
                body.kind(), body.pieceCount(), body.netWeight(), body.grossWeight(), body.weightUnit(), body.purity(),
                body.clearAsLoss(), body.addToFinished(), body.feeOffset(), body.autoReturn(), body.inferWeight()));
    }

    public record RecordRequest(
            String requestId,
            String sourceLineId,
            String outputMovementId,
            String kind,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            BigDecimal grossWeight,
            String weightUnit,
            String purity,
            boolean clearAsLoss,
            boolean addToFinished,
            BigDecimal feeOffset,
            boolean autoReturn,
            boolean inferWeight
    ) {
    }
}
