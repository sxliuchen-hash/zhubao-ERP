package com.zhubao.erp.business.domain;

import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;

public interface BusinessWorkspaceRepository {

    InboundWorkspaceData inbound();

    ProductionWorkspaceData production();

    QualityWorkspaceData quality();

    InventoryWorkspaceData inventory();

    MobileWorkspaceData mobile();
}
