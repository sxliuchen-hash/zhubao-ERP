package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessReviewData.BusinessWorkspaceSummary;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.BusinessWorkspaceRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.stereotype.Service;

@Service
public class BusinessWorkspaceService {

    private static final OperatorContext REVIEW_CONTEXT = new OperatorContext("system-review", RoleCode.ADMIN, DataScope.ALL);

    private final BusinessWorkspaceRepository businessWorkspaceRepository;
    private final BusinessWorkspaceDataScopeFilter dataScopeFilter = new BusinessWorkspaceDataScopeFilter();

    public BusinessWorkspaceService(BusinessWorkspaceRepository businessWorkspaceRepository) {
        this.businessWorkspaceRepository = businessWorkspaceRepository;
    }

    public InboundWorkspaceData inbound() {
        return inbound(REVIEW_CONTEXT);
    }

    public InboundWorkspaceData inbound(OperatorContext operatorContext) {
        return dataScopeFilter.filter(businessWorkspaceRepository.inbound(), operatorContext);
    }

    public ProductionWorkspaceData production() {
        return production(REVIEW_CONTEXT);
    }

    public ProductionWorkspaceData production(OperatorContext operatorContext) {
        return dataScopeFilter.filter(businessWorkspaceRepository.production(), operatorContext);
    }

    public QualityWorkspaceData quality() {
        return quality(REVIEW_CONTEXT);
    }

    public QualityWorkspaceData quality(OperatorContext operatorContext) {
        return dataScopeFilter.filter(businessWorkspaceRepository.quality(), operatorContext);
    }

    public InventoryWorkspaceData inventory() {
        return inventory(REVIEW_CONTEXT);
    }

    public InventoryWorkspaceData inventory(OperatorContext operatorContext) {
        return dataScopeFilter.filter(businessWorkspaceRepository.inventory(), operatorContext);
    }

    public MobileWorkspaceData mobile() {
        return mobile(REVIEW_CONTEXT);
    }

    public MobileWorkspaceData mobile(OperatorContext operatorContext) {
        return dataScopeFilter.filter(businessWorkspaceRepository.mobile(), operatorContext);
    }

    public BusinessWorkspaceSummary summary() {
        return summary(REVIEW_CONTEXT);
    }

    public BusinessWorkspaceSummary summary(OperatorContext operatorContext) {
        return new BusinessWorkspaceSummary(
                inbound(operatorContext).orders().size(),
                production(operatorContext).workOrders().size(),
                quality(operatorContext).cases().size(),
                inventory(operatorContext).ledgerEntries().size(),
                mobile(operatorContext).tasks().size()
        );
    }
}
