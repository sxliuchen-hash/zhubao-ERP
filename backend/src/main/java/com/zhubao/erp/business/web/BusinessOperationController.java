package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.CustomerReturnCommand;
import com.zhubao.erp.business.domain.CustomerReturnResult;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.business.domain.OutboundShipmentResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.QualityFreezeResult;
import com.zhubao.erp.business.domain.QualityReleaseResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ReportReworkCommand;
import com.zhubao.erp.business.domain.ReleaseQualityStockCommand;
import com.zhubao.erp.business.domain.ReworkDispatchResult;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
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

    @PostMapping("/rework-dispatches")
    public ApiResponse<ReworkDispatchResult> dispatchRework(@Valid @RequestBody DispatchReworkCommand command) {
        return ApiResponse.ok(businessOperationService.dispatchRework(command));
    }

    @PostMapping("/rework-reports")
    public ApiResponse<ReworkReportResult> reportRework(@Valid @RequestBody ReportReworkCommand command) {
        return ApiResponse.ok(businessOperationService.reportRework(command));
    }

    @PostMapping("/quality-releases")
    public ApiResponse<QualityReleaseResult> releaseQualityStock(@Valid @RequestBody ReleaseQualityStockCommand command) {
        return ApiResponse.ok(businessOperationService.releaseQualityStock(command));
    }

    @PostMapping("/outbound-shipments")
    public ApiResponse<OutboundShipmentResult> shipFinishedStock(@Valid @RequestBody ShipFinishedStockCommand command) {
        return ApiResponse.ok(businessOperationService.shipFinishedStock(command));
    }

    @PostMapping("/customer-returns")
    public ApiResponse<CustomerReturnResult> returnCustomerShipment(@Valid @RequestBody CustomerReturnCommand command) {
        return ApiResponse.ok(businessOperationService.returnCustomerShipment(command));
    }

    @PostMapping("/loss-approvals")
    public ApiResponse<LossApprovalResult> approveLoss(@Valid @RequestBody ApproveLossCommand command) {
        return ApiResponse.ok(businessOperationService.approveLoss(command));
    }
}
