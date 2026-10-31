package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkSupplementService;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementView;
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

/** 补料和退补。不覆盖最初来料，不另记补料费。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/supplements")
public class GoldworkSupplementController {

    private final GoldworkSupplementService supplements;
    private final OperatorContextResolver actors;

    public GoldworkSupplementController(GoldworkSupplementService supplements, OperatorContextResolver actors) {
        this.supplements = supplements;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkSupplementView>> list(@PathVariable String goldworkCode, HttpServletRequest request) {
        return ApiResponse.ok(supplements.list(actors.resolve(request), goldworkCode));
    }

    @PostMapping
    public ApiResponse<GoldworkSupplementView> issue(@PathVariable String goldworkCode, @RequestBody IssueRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(supplements.issue(
                body.requestId(), actors.resolve(request), goldworkCode, body.originalLineId(), body.sourceId(),
                body.sourceLineNo(), body.purpose(), body.pieceCount(), body.netWeight(), body.grossWeight(),
                body.weightUnit(), body.purity(), body.addOntoOriginal(), body.changeOwner(), body.feeAmount()));
    }

    @PostMapping("/returns")
    public ApiResponse<GoldworkSupplementView> giveBack(@PathVariable String goldworkCode, @RequestBody ReturnRequest body,
                                                        HttpServletRequest request) {
        return ApiResponse.ok(supplements.giveBack(
                body.requestId(), actors.resolve(request), goldworkCode, body.originalLineId(), body.priorSupplementId(),
                body.purpose(), body.pieceCount(), body.netWeight(), body.grossWeight(), body.weightUnit(), body.purity(),
                body.addOntoOriginal(), body.changeOwner(), body.feeAmount()));
    }

    public record IssueRequest(
            String requestId,
            String originalLineId,
            String sourceId,
            Integer sourceLineNo,
            String purpose,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            BigDecimal grossWeight,
            String weightUnit,
            String purity,
            boolean addOntoOriginal,
            boolean changeOwner,
            BigDecimal feeAmount
    ) {
    }

    public record ReturnRequest(
            String requestId,
            String originalLineId,
            String priorSupplementId,
            String purpose,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            BigDecimal grossWeight,
            String weightUnit,
            String purity,
            boolean addOntoOriginal,
            boolean changeOwner,
            BigDecimal feeAmount
    ) {
    }
}
