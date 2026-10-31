package com.zhubao.erp.sampling.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.sampling.app.SamplingOrderService;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
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
 * 独立打样单。查询要打样查阅，建单要打样办理。不接收母单号。
 */
@RestController
@RequestMapping("/api/sampling/orders")
public class SamplingOrderController {

    private final SamplingOrderService orders;
    private final OperatorContextResolver actors;

    public SamplingOrderController(SamplingOrderService orders, OperatorContextResolver actors) {
        this.orders = orders;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<SamplingOrderView>> list(
            @RequestParam(required = false) Long customerId, HttpServletRequest request) {
        return ApiResponse.ok(orders.list(actors.resolve(request), customerId));
    }

    @PostMapping
    public ApiResponse<SamplingOrderView> open(@RequestBody OpenRequest body, HttpServletRequest request) {
        return ApiResponse.ok(orders.open(
                body.requestId(),
                actors.resolve(request),
                body.customerId(),
                body.materialIntent(),
                body.requirement(),
                body.ownerName(),
                body.motherOrderCode(),
                body.samplingCode()
        ));
    }

    public record OpenRequest(
            String requestId,
            Long customerId,
            String materialIntent,
            String requirement,
            String ownerName,
            String motherOrderCode,
            String samplingCode
    ) {
    }
}
