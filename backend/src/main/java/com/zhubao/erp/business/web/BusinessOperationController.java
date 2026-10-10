package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MotherOrderSplitService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.app.QualityRequirementService;
import com.zhubao.erp.business.domain.AfterSalesReviewCommand;
import com.zhubao.erp.business.domain.AfterSalesReviewResult;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.CustomerReturnCommand;
import com.zhubao.erp.business.domain.CustomerReturnResult;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.app.MobileIntakeService;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeLookup;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.business.domain.OutboundShipmentResult;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.QualityCheckCommand;
import com.zhubao.erp.business.domain.QualityCheckResult;
import com.zhubao.erp.business.domain.QualityCheckView;
import com.zhubao.erp.business.domain.QualityFreezeResult;
import com.zhubao.erp.business.domain.QualityReleaseResult;
import com.zhubao.erp.business.domain.QualityReworkCommand;
import com.zhubao.erp.business.domain.QualityReworkResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ReportReworkCommand;
import com.zhubao.erp.business.domain.ReleaseQualityStockCommand;
import com.zhubao.erp.business.domain.ReworkDispatchResult;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.business.domain.MotherOrderSplitView;
import com.zhubao.erp.business.domain.ProcessStationReportCommand;
import com.zhubao.erp.business.domain.ProcessStationReportResult;
import com.zhubao.erp.business.domain.ProcessStationView;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderRequest;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthUserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/operations")
public class BusinessOperationController {

    private final BusinessOperationService businessOperationService;
    private final MobileIntakeService mobileIntakeService;
    private final MotherOrderSplitService motherOrderSplitService;
    private final OperatorContextResolver operatorContextResolver;
    private final ProcessStationService processStationService;
    private final AuthSessionService authSessionService;
    private final QualityRequirementService qualityRequirementService;

    public BusinessOperationController(
            BusinessOperationService businessOperationService,
            MobileIntakeService mobileIntakeService,
            MotherOrderSplitService motherOrderSplitService,
            OperatorContextResolver operatorContextResolver
    ) {
        this(
                businessOperationService,
                mobileIntakeService,
                motherOrderSplitService,
                operatorContextResolver,
                null,
                null,
                null
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BusinessOperationController(
            BusinessOperationService businessOperationService,
            MobileIntakeService mobileIntakeService,
            MotherOrderSplitService motherOrderSplitService,
            OperatorContextResolver operatorContextResolver,
            ProcessStationService processStationService,
            AuthSessionService authSessionService,
            QualityRequirementService qualityRequirementService
    ) {
        this.businessOperationService = businessOperationService;
        this.mobileIntakeService = mobileIntakeService;
        this.motherOrderSplitService = motherOrderSplitService;
        this.operatorContextResolver = operatorContextResolver;
        this.processStationService = processStationService;
        this.authSessionService = authSessionService;
        this.qualityRequirementService = qualityRequirementService;
    }

    @GetMapping("/mobile-intakes")
    public ApiResponse<MobileIntakeLookup> lookupMobileIntake(@RequestParam String courierNo) {
        return ApiResponse.ok(mobileIntakeService.lookup(courierNo));
    }

    @PostMapping("/mobile-intakes")
    public ApiResponse<MobileIntakeResult> intakeMobile(
            @Valid @RequestBody MobileIntakeCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(mobileIntakeService.intake(command, operatorContextResolver.resolve(request)));
    }

    @GetMapping("/mother-orders/{code}")
    public ApiResponse<MobileIntakeResult> motherOrder(@PathVariable String code) {
        return ApiResponse.ok(mobileIntakeService.view(code));
    }

    @GetMapping("/mother-orders/{code}/split")
    public ApiResponse<MotherOrderSplitView> motherSplit(@PathVariable String code) {
        return ApiResponse.ok(motherOrderSplitService.view(code));
    }

    @PostMapping("/mother-orders/{code}/keep")
    public ApiResponse<MotherOrderSplitView> keepMotherOrder(
            @PathVariable String code,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(motherOrderSplitService.keep(code, operatorContextResolver.resolve(request).operator()));
    }

    @PostMapping("/mother-orders/{code}/splits")
    public ApiResponse<MotherOrderSplitView> splitMotherOrder(
            @PathVariable String code,
            @Valid @RequestBody SplitMotherOrderRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(motherOrderSplitService.split(new SplitMotherOrderCommand(
                code,
                operatorContextResolver.resolve(request).operator(),
                body.lines()
        )));
    }

    @PostMapping("/inbound-confirmations")
    public ApiResponse<InboundConfirmationResult> confirmInbound(@Valid @RequestBody ConfirmInboundCommand command) {
        return ApiResponse.ok(businessOperationService.confirmInbound(command));
    }

    @GetMapping("/process-stations")
    public ApiResponse<ProcessStationView> lookupProcessStation(
            @RequestParam String code,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(requireProcessStationService().lookup(code, requireStationAccount(request)));
    }

    @PostMapping("/process-stations")
    public ApiResponse<ProcessStationReportResult> reportProcessStation(
            @Valid @RequestBody ProcessStationReportCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(requireProcessStationService().report(
                command.code(),
                command.process(),
                requireStationAccount(request)
        ));
    }

    @PostMapping("/production-reports")
    public ApiResponse<ProductionReportResult> reportProduction(@Valid @RequestBody ReportProductionCommand command) {
        return ApiResponse.ok(businessOperationService.reportProduction(command));
    }

    @GetMapping("/quality-checks")
    public ApiResponse<QualityCheckView> lookupQualityCheck(
            @RequestParam String code,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(requireQualityRequirementService().lookup(code, requireQualityAccount(request)));
    }

    @PostMapping("/quality-checks")
    public ApiResponse<QualityCheckResult> submitQualityCheck(
            @Valid @RequestBody QualityCheckCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(requireQualityRequirementService().submit(command, requireQualityAccount(request)));
    }

    @PostMapping("/quality-checks/rework")
    public ApiResponse<QualityReworkResult> openQualityRework(
            @Valid @RequestBody QualityReworkCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(requireQualityRequirementService().openRework(command.code(), requireQualityAccount(request)));
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

    @PostMapping("/after-sales-reviews")
    public ApiResponse<AfterSalesReviewResult> reviewAfterSalesReturn(@Valid @RequestBody AfterSalesReviewCommand command) {
        return ApiResponse.ok(businessOperationService.reviewAfterSalesReturn(command));
    }

    @PostMapping("/loss-approvals")
    public ApiResponse<LossApprovalResult> approveLoss(@Valid @RequestBody ApproveLossCommand command) {
        return ApiResponse.ok(businessOperationService.approveLoss(command));
    }

    private ProcessStationService requireProcessStationService() {
        if (processStationService == null) {
            throw new BusinessException("岗位报工尚未启用");
        }
        return processStationService;
    }

    private AuthUserAccount requireStationAccount(HttpServletRequest request) {
        if (authSessionService == null) {
            throw new BusinessException("请先登录后再做岗位报工");
        }
        return authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再做岗位报工"));
    }

    private QualityRequirementService requireQualityRequirementService() {
        if (qualityRequirementService == null) {
            throw new BusinessException("质检对照尚未启用");
        }
        return qualityRequirementService;
    }

    private AuthUserAccount requireQualityAccount(HttpServletRequest request) {
        if (authSessionService == null) {
            throw new BusinessException("请先登录后再做质检对照");
        }
        return authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再做质检对照"));
    }
}
