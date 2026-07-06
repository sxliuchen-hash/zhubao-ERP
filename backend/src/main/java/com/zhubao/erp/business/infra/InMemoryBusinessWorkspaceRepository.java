package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.BusinessWorkspaceRepository;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryBusinessWorkspaceRepository implements BusinessWorkspaceRepository {

    private final BusinessWorkspaceSeedData seedData;

    public InMemoryBusinessWorkspaceRepository(BusinessWorkspaceSeedData seedData) {
        this.seedData = seedData;
    }

    @Override
    public InboundWorkspaceData inbound() {
        return seedData.inbound();
    }

    @Override
    public ProductionWorkspaceData production() {
        return seedData.production();
    }

    @Override
    public QualityWorkspaceData quality() {
        return seedData.quality();
    }

    @Override
    public InventoryWorkspaceData inventory() {
        return seedData.inventory();
    }

    @Override
    public MobileWorkspaceData mobile() {
        return seedData.mobile();
    }
}
