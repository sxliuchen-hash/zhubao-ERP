package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.BusinessWorkspaceService;
import com.zhubao.erp.business.domain.BusinessReviewData.BusinessWorkspaceSummary;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/workspaces")
public class BusinessWorkspaceController {

    private final BusinessWorkspaceService businessWorkspaceService;

    public BusinessWorkspaceController(BusinessWorkspaceService businessWorkspaceService) {
        this.businessWorkspaceService = businessWorkspaceService;
    }

    @GetMapping("/inbound")
    public ApiResponse<InboundWorkspaceData> inbound() {
        return ApiResponse.ok(businessWorkspaceService.inbound());
    }

    @GetMapping("/production")
    public ApiResponse<ProductionWorkspaceData> production() {
        return ApiResponse.ok(businessWorkspaceService.production());
    }

    @GetMapping("/quality")
    public ApiResponse<QualityWorkspaceData> quality() {
        return ApiResponse.ok(businessWorkspaceService.quality());
    }

    @GetMapping("/inventory")
    public ApiResponse<InventoryWorkspaceData> inventory() {
        return ApiResponse.ok(businessWorkspaceService.inventory());
    }

    @GetMapping("/mobile")
    public ApiResponse<MobileWorkspaceData> mobile() {
        return ApiResponse.ok(businessWorkspaceService.mobile());
    }

    @GetMapping("/summary")
    public ApiResponse<BusinessWorkspaceSummary> summary() {
        return ApiResponse.ok(businessWorkspaceService.summary());
    }
}
