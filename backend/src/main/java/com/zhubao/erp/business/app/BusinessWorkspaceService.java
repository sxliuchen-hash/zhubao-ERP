package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessReviewData.BusinessWorkspaceSummary;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.BusinessWorkspaceRepository;
import org.springframework.stereotype.Service;

@Service
public class BusinessWorkspaceService {

    private final BusinessWorkspaceRepository businessWorkspaceRepository;

    public BusinessWorkspaceService(BusinessWorkspaceRepository businessWorkspaceRepository) {
        this.businessWorkspaceRepository = businessWorkspaceRepository;
    }

    public InboundWorkspaceData inbound() {
        return businessWorkspaceRepository.inbound();
    }

    public ProductionWorkspaceData production() {
        return businessWorkspaceRepository.production();
    }

    public QualityWorkspaceData quality() {
        return businessWorkspaceRepository.quality();
    }

    public InventoryWorkspaceData inventory() {
        return businessWorkspaceRepository.inventory();
    }

    public MobileWorkspaceData mobile() {
        return businessWorkspaceRepository.mobile();
    }

    public BusinessWorkspaceSummary summary() {
        return new BusinessWorkspaceSummary(
                inbound().orders().size(),
                production().workOrders().size(),
                quality().cases().size(),
                inventory().ledgerEntries().size(),
                mobile().tasks().size()
        );
    }
}
