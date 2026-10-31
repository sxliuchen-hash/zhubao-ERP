package com.zhubao.erp.sampling.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sampling.app.SamplingFeeService;
import com.zhubao.erp.sampling.domain.SamplingFeeView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 按一次交付确认样费或明确免收。不写工资，也不抵大货。 */
@RestController
@RequestMapping("/api/sampling/orders/{samplingCode}/deliveries/{deliveryId}/fee")
public class SamplingFeeController {

    private final SamplingFeeService fees;
    private final OperatorContextResolver actors;

    public SamplingFeeController(SamplingFeeService fees, OperatorContextResolver actors) {
        this.fees = fees;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<SamplingFeeView> find(@PathVariable String samplingCode, @PathVariable String deliveryId,
                                            HttpServletRequest request) {
        return ApiResponse.ok(fees.find(actors.resolve(request), samplingCode, deliveryId));
    }

    @PostMapping
    public ApiResponse<SamplingFeeView> settle(@PathVariable String samplingCode, @PathVariable String deliveryId,
                                              @RequestBody SettleRequest body, HttpServletRequest request) {
        return ApiResponse.ok(fees.settle(
                body.requestId(), actors.resolve(request), samplingCode, deliveryId, body.amount(), body.waive(),
                body.reason(), body.motherOrderCode(), body.settleWages()));
    }

    public record SettleRequest(
            String requestId,
            BigDecimal amount,
            boolean waive,
            String reason,
            String motherOrderCode,
            boolean settleWages
    ) {
    }
}
