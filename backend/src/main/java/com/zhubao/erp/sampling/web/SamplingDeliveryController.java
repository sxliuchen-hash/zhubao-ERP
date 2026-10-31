package com.zhubao.erp.sampling.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sampling.app.SamplingDeliveryService;
import com.zhubao.erp.sampling.domain.SamplingDeliveryView;
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

/** 打样分次交付。不创建普通发货，也不把打样单标成完成。 */
@RestController
@RequestMapping("/api/sampling/orders/{samplingCode}/deliveries")
public class SamplingDeliveryController {

    private final SamplingDeliveryService deliveries;
    private final OperatorContextResolver actors;

    public SamplingDeliveryController(SamplingDeliveryService deliveries, OperatorContextResolver actors) {
        this.deliveries = deliveries;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<SamplingDeliveryView>> list(@PathVariable String samplingCode, HttpServletRequest request) {
        return ApiResponse.ok(deliveries.list(actors.resolve(request), samplingCode));
    }

    @PostMapping
    public ApiResponse<SamplingDeliveryView> deliver(@PathVariable String samplingCode, @RequestBody DeliverRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(deliveries.deliver(
                body.requestId(), actors.resolve(request), samplingCode, body.versionKind(), body.versionNo(),
                body.materialUseId(), body.quantity(), body.unit(), body.credentialCode()));
    }

    public record DeliverRequest(
            String requestId,
            String versionKind,
            Integer versionNo,
            String materialUseId,
            BigDecimal quantity,
            String unit,
            String credentialCode
    ) {
    }
}
