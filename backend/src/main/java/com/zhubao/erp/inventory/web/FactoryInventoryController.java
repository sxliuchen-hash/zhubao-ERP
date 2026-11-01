package com.zhubao.erp.inventory.web;

import com.zhubao.erp.inventory.app.*;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/** 本厂库存查询与工具入库，写入仍由原有服务核验权限与请求回放。 */
@RestController
@RequestMapping("/api/inventory/factory-items")
public class FactoryInventoryController {
    private final FactoryInventoryQuery query; private final ToolCustodyService tools; private final OperatorContextResolver actors;
    public FactoryInventoryController(FactoryInventoryQuery query,ToolCustodyService tools,OperatorContextResolver actors) { this.query=query; this.tools=tools; this.actors=actors; }
    @GetMapping("/{itemId}/inventory") public ApiResponse<FactoryInventoryQuery.Position> read(@PathVariable String itemId,@RequestParam(required=false) String warehouse,HttpServletRequest request) {
        return ApiResponse.ok(query.read(actors.requireSession(request),itemId,warehouse));
    }
    @PostMapping("/{itemId}/tool-placements") public ApiResponse<String> place(@PathVariable String itemId,@RequestBody Placement body,HttpServletRequest request) {
        var actor=actors.requireSession(request);
        if (body.pieceCode()!=null && !body.pieceCode().isBlank()) tools.placePiece(body.requestId(),actor,itemId,body.warehouseCode(),body.pieceCode());
        else tools.place(body.requestId(),actor,itemId,body.warehouseCode(),body.quantity());
        return ApiResponse.ok(itemId);
    }
    public record Placement(String requestId,String warehouseCode,BigDecimal quantity,String pieceCode) {}
}
