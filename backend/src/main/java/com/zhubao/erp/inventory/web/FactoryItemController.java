package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.FactoryItemService;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本厂物品档案入口。只维护类别和规格身份，不写领用、耗用或库存余额。
 */
@RestController
@RequestMapping("/api/inventory/factory-items")
public class FactoryItemController {

    private final FactoryItemService items;
    private final OperatorContextResolver actors;

    public FactoryItemController(FactoryItemService items, OperatorContextResolver actors) {
        this.items = items;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<FactoryItem>> list(@RequestParam(required = false) String materialName, HttpServletRequest request) {
        return ApiResponse.ok(items.list(actors.resolve(request), materialName));
    }

    @PostMapping
    public ApiResponse<FactoryItem> register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        FactoryItem saved = items.register(
                body.requestId(), actor, category(body.category()), body.materialName(), body.specCode(), body.unit(),
                timing(body.consumptionTiming()), custody(body.custodyMode()));
        return ApiResponse.ok(saved);
    }

    @PostMapping("/{itemId}/policy")
    public ApiResponse<FactoryItem> recordPolicy(@PathVariable String itemId, @RequestBody PolicyRequest body,
                                                 HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(items.recordPolicy(
                body.requestId(), actor, itemId, body.version(), body.materialName(),
                timing(body.consumptionTiming()), custody(body.custodyMode())));
    }

    private static FactoryItemCategory category(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("请选择管理类别");
        }
        try {
            return FactoryItemCategory.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("管理类别不正确");
        }
    }

    private static ConsumptionTiming timing(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ConsumptionTiming.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("计耗方式不正确");
        }
    }

    private static CustodyMode custody(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CustodyMode.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("保管方式不正确");
        }
    }

    public record RegisterRequest(
            String requestId,
            String category,
            String materialName,
            String specCode,
            String unit,
            String consumptionTiming,
            String custodyMode
    ) {
    }

    public record PolicyRequest(
            String requestId,
            int version,
            String materialName,
            String consumptionTiming,
            String custodyMode
    ) {
    }
}
