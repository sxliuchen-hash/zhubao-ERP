package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkDeliveryService;
import com.zhubao.erp.goldwork.domain.GoldworkDeliveryView;
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

/** 金工交付。不按克数乘金价，不写应收。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/deliveries")
public class GoldworkDeliveryController {

    private final GoldworkDeliveryService deliveries;
    private final OperatorContextResolver actors;

    public GoldworkDeliveryController(GoldworkDeliveryService deliveries, OperatorContextResolver actors) {
        this.deliveries = deliveries;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkDeliveryView>> list(@PathVariable String goldworkCode, HttpServletRequest request) {
        return ApiResponse.ok(deliveries.list(actors.resolve(request), goldworkCode));
    }

    @PostMapping
    public ApiResponse<GoldworkDeliveryView> deliver(@PathVariable String goldworkCode, @RequestBody DeliverRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(deliveries.deliver(
                body.requestId(), actors.resolve(request), goldworkCode, body.qualityId(), body.pieceCount(),
                body.netWeight(), body.weightUnit(), body.goldPrice(), body.amount(), body.shipAsProcessing(),
                body.countCustomerGoldAsSales()));
    }

    public record DeliverRequest(
            String requestId,
            String qualityId,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            String weightUnit,
            BigDecimal goldPrice,
            BigDecimal amount,
            boolean shipAsProcessing,
            boolean countCustomerGoldAsSales
    ) {
    }
}
