package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.BusinessWorkspaceService;
import com.zhubao.erp.business.domain.BusinessReviewData.BusinessWorkspaceSummary;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/workspaces")
public class BusinessWorkspaceController {

    private final BusinessWorkspaceService businessWorkspaceService;
    private final OperatorContextResolver operatorContextResolver;

    public BusinessWorkspaceController(
            BusinessWorkspaceService businessWorkspaceService,
            OperatorContextResolver operatorContextResolver
    ) {
        this.businessWorkspaceService = businessWorkspaceService;
        this.operatorContextResolver = operatorContextResolver;
    }

    @GetMapping("/inbound")
    public ApiResponse<InboundWorkspaceData> inbound(HttpServletRequest request) {
        return ApiResponse.ok(businessWorkspaceService.inbound(operatorContextResolver.resolve(request)));
    }

    @GetMapping("/production")
    public ApiResponse<ProductionWorkspaceData> production(HttpServletRequest request) {
        return ApiResponse.ok(businessWorkspaceService.production(operatorContextResolver.resolve(request)));
    }

    @GetMapping("/quality")
    public ApiResponse<QualityWorkspaceData> quality(HttpServletRequest request) {
        return ApiResponse.ok(businessWorkspaceService.quality(operatorContextResolver.resolve(request)));
    }

    @GetMapping("/inventory")
    public ApiResponse<InventoryWorkspaceData> inventory(HttpServletRequest request) {
        return ApiResponse.ok(businessWorkspaceService.inventory(operatorContextResolver.resolve(request)));
    }

    @GetMapping("/mobile")
    public ApiResponse<MobileWorkspaceData> mobile(HttpServletRequest request) {
        return ApiResponse.ok(businessWorkspaceService.mobile(operatorContextResolver.resolve(request)));
    }

    @GetMapping("/summary")
    public ApiResponse<BusinessWorkspaceSummary> summary(HttpServletRequest request) {
        return ApiResponse.ok(businessWorkspaceService.summary(operatorContextResolver.resolve(request)));
    }
}
