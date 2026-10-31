package com.zhubao.erp.sampling.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sampling.app.SamplingOperationService;
import com.zhubao.erp.sampling.domain.SamplingOperationView;
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

/**
 * 打样节点报工。不接收普通加工工序，也不开通母单。
 */
@RestController
@RequestMapping("/api/sampling/orders/{samplingCode}/operations")
public class SamplingOperationController {

    private final SamplingOperationService operations;
    private final OperatorContextResolver actors;

    public SamplingOperationController(SamplingOperationService operations, OperatorContextResolver actors) {
        this.operations = operations;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<SamplingOperationView>> list(@PathVariable String samplingCode, HttpServletRequest request) {
        return ApiResponse.ok(operations.list(actors.resolve(request), samplingCode));
    }

    @PostMapping
    public ApiResponse<SamplingOperationView> report(@PathVariable String samplingCode, @RequestBody ReportRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(operations.report(
                body.requestId(), actors.resolve(request), samplingCode, body.stepId(), body.versionKind(),
                body.versionNo(), body.makingChoices(), body.quantity(), body.unit(), body.attachmentCode()));
    }

    public record ReportRequest(
            String requestId,
            String stepId,
            String versionKind,
            Integer versionNo,
            List<String> makingChoices,
            BigDecimal quantity,
            String unit,
            String attachmentCode
    ) {
    }
}
