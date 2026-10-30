package com.zhubao.erp.inventory.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.CompanyMaterialReceiptService;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 公司料入库入口。采购应付与入库同一次提交，不另开采购审批或销售。
 */
@RestController
@RequestMapping("/api/inventory/company-material-receipts")
public class CompanyMaterialReceiptController {

    private final CompanyMaterialReceiptService receipts;
    private final OperatorContextResolver actors;

    public CompanyMaterialReceiptController(CompanyMaterialReceiptService receipts, OperatorContextResolver actors) {
        this.receipts = receipts;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<CompanyMaterialReceipt> receive(@RequestBody ReceiveRequest body, HttpServletRequest request) {
        OperatorContext actor = actors.resolve(request);
        return ApiResponse.ok(receipts.receive(
                body.requestId(), actor, body.itemId(), body.quantity(), body.weight(), body.unit(),
                body.warehouseCode(), body.bizDate(), source(body.sourceKind()), body.supplierName(),
                body.amount(), body.coveredPayableCode()));
    }

    private static CompanyMaterialSourceKind source(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("请选择入库来源");
        }
        try {
            return CompanyMaterialSourceKind.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("入库来源不正确");
        }
    }

    public record ReceiveRequest(
            String requestId,
            String itemId,
            BigDecimal quantity,
            BigDecimal weight,
            String unit,
            String warehouseCode,
            String bizDate,
            String sourceKind,
            String supplierName,
            BigDecimal amount,
            String coveredPayableCode
    ) {
    }
}
