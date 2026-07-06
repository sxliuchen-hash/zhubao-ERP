package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.QualityFreezeResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/operations")
public class BusinessOperationController {

    private final BusinessOperationService businessOperationService;

    public BusinessOperationController(BusinessOperationService businessOperationService) {
        this.businessOperationService = businessOperationService;
    }

    @PostMapping("/inbound-confirmations")
    public ApiResponse<InboundConfirmationResult> confirmInbound(@Valid @RequestBody ConfirmInboundCommand command) {
        return ApiResponse.ok(businessOperationService.confirmInbound(command));
    }

    @PostMapping("/production-reports")
    public ApiResponse<ProductionReportResult> reportProduction(@Valid @RequestBody ReportProductionCommand command) {
        return ApiResponse.ok(businessOperationService.reportProduction(command));
    }

    @PostMapping("/quality-freezes")
    public ApiResponse<QualityFreezeResult> freezeQualityStock(@Valid @RequestBody FreezeQualityStockCommand command) {
        return ApiResponse.ok(businessOperationService.freezeQualityStock(command));
    }
}
