package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessReviewData.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.stereotype.Service;

/** 正式工作台统一读取业务单据与台账。 */
@Service
public class BusinessWorkspaceService {
    private static final OperatorContext SYSTEM_CONTEXT = new OperatorContext("system-workspace", RoleCode.ADMIN, DataScope.ALL);
    private final FormalBusinessWorkspaceQuery formalQuery;

    public BusinessWorkspaceService(BusinessDocumentRepository documents, ProgressBoardService progress,
                                    StockLedgerService stocks, ApprovalService approvals, AuthUserAccountRepository accounts) {
        this.formalQuery = new FormalBusinessWorkspaceQuery(documents, progress, stocks, approvals, accounts);
    }

    public InboundWorkspaceData inbound() { return inbound(SYSTEM_CONTEXT); }
    public InboundWorkspaceData inbound(OperatorContext operator) {
        return formalQuery.inbound(operator);
    }
    public ProductionWorkspaceData production() { return production(SYSTEM_CONTEXT); }
    public ProductionWorkspaceData production(OperatorContext operator) {
        return formalQuery.production(operator);
    }
    public QualityWorkspaceData quality() { return quality(SYSTEM_CONTEXT); }
    public QualityWorkspaceData quality(OperatorContext operator) {
        return formalQuery.quality(operator);
    }
    public InventoryWorkspaceData inventory() { return inventory(SYSTEM_CONTEXT); }
    public InventoryWorkspaceData inventory(OperatorContext operator) {
        return formalQuery.inventory(operator);
    }
    public MobileWorkspaceData mobile() { return mobile(SYSTEM_CONTEXT); }
    public MobileWorkspaceData mobile(OperatorContext operator) {
        return formalQuery.mobile(operator);
    }
    public BusinessWorkspaceSummary summary() { return summary(SYSTEM_CONTEXT); }
    public BusinessWorkspaceSummary summary(OperatorContext operator) {
        return formalQuery.summary(operator);
    }
}
