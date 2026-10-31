package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkMovementService;
import com.zhubao.erp.goldwork.domain.GoldworkMovementView;
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

/** 金工投入产出。不推算损耗，也不把毛重写成净金。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/movements")
public class GoldworkMovementController {

    private final GoldworkMovementService movements;
    private final OperatorContextResolver actors;

    public GoldworkMovementController(GoldworkMovementService movements, OperatorContextResolver actors) {
        this.movements = movements;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkMovementView>> list(@PathVariable String goldworkCode, HttpServletRequest request) {
        return ApiResponse.ok(movements.list(actors.resolve(request), goldworkCode));
    }

    @PostMapping
    public ApiResponse<GoldworkMovementView> record(@PathVariable String goldworkCode, @RequestBody RecordRequest body,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(movements.record(
                body.requestId(), actors.resolve(request), goldworkCode, body.stepId(), body.kind(), body.sourceLineId(),
                body.pieceCount(), body.netWeight(), body.grossWeight(), body.weightUnit(), body.purity(),
                body.disposition(), body.conversionNote(), body.useGrossAsNet(), body.inferShortage(),
                body.conversionFactor(), body.enforcePieceConservation()));
    }

    public record RecordRequest(
            String requestId,
            String stepId,
            String kind,
            String sourceLineId,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            BigDecimal grossWeight,
            String weightUnit,
            String purity,
            String disposition,
            String conversionNote,
            boolean useGrossAsNet,
            boolean inferShortage,
            BigDecimal conversionFactor,
            boolean enforcePieceConservation
    ) {
    }
}
