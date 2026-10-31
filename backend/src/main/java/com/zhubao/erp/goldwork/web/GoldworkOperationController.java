package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkOperationService;
import com.zhubao.erp.goldwork.domain.GoldworkOperationView;
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

/** 金工拍照报工。不写库存或工资。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/operations")
public class GoldworkOperationController {

    private final GoldworkOperationService operations;
    private final OperatorContextResolver actors;

    public GoldworkOperationController(GoldworkOperationService operations, OperatorContextResolver actors) {
        this.operations = operations;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkOperationView>> list(@PathVariable String goldworkCode, HttpServletRequest request) {
        return ApiResponse.ok(operations.list(actors.resolve(request), goldworkCode));
    }

    @PostMapping
    public ApiResponse<GoldworkOperationView> report(@PathVariable String goldworkCode, @RequestBody ReportRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(operations.report(
                body.requestId(), actors.resolve(request), goldworkCode, body.stepId(), body.pieceCount(),
                body.netWeight(), body.weightUnit(), body.purity(), body.attachmentCode(),
                body.settleWage(), body.postStock(), body.closeWip()));
    }

    public record ReportRequest(
            String requestId,
            String stepId,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            String weightUnit,
            String purity,
            String attachmentCode,
            boolean settleWage,
            boolean postStock,
            boolean closeWip
    ) {
    }
}
