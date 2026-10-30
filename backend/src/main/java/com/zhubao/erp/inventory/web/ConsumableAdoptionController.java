package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.inventory.app.ConsumableOpeningAdoptionService;
import com.zhubao.erp.inventory.domain.ConsumableAdoption;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旧耗材结存接续。不把期初再记成采购或应付。
 */
@RestController
@RequestMapping("/api/inventory/consumable-adoptions")
public class ConsumableAdoptionController {

    private final ConsumableOpeningAdoptionService adoptions;
    private final OperatorContextResolver actors;

    public ConsumableAdoptionController(ConsumableOpeningAdoptionService adoptions, OperatorContextResolver actors) {
        this.adoptions = adoptions;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<ConsumableAdoption> adopt(@RequestBody AdoptRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(adoptions.adopt(body.requestId(), actor, body.oldItemCode(), body.specCode(), body.warehouseCode()));
    }

    public record AdoptRequest(String requestId, String oldItemCode, String specCode, String warehouseCode) {
    }
}
