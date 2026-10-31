package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkOrderService;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 独立金工单。不接收母单号或打样号。 */
@RestController
@RequestMapping("/api/goldwork/orders")
public class GoldworkOrderController {

    private final GoldworkOrderService orders;
    private final OperatorContextResolver actors;

    public GoldworkOrderController(GoldworkOrderService orders, OperatorContextResolver actors) {
        this.orders = orders;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkOrderView>> list(
            @RequestParam(required = false) Long customerId, HttpServletRequest request) {
        return ApiResponse.ok(orders.list(actors.resolve(request), customerId));
    }

    @PostMapping
    public ApiResponse<GoldworkOrderView> open(@RequestBody OpenRequest body, HttpServletRequest request) {
        return ApiResponse.ok(orders.open(
                body.requestId(), actors.resolve(request), body.customerId(), body.materialIntent(),
                body.requirement(), body.ownerName(), body.routeSteps(), body.motherOrderCode(), body.samplingCode()
        ));
    }

    public record OpenRequest(
            String requestId,
            Long customerId,
            String materialIntent,
            String requirement,
            String ownerName,
            String routeSteps,
            String motherOrderCode,
            String samplingCode
    ) {
    }
}
