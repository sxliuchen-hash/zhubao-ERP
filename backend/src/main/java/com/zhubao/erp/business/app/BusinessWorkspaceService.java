package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessWorkspaceRepository;
import com.zhubao.erp.business.domain.BusinessReviewData.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 正式工作台读取业务单据与台账；评审快照显式启用，且不与正式单据混合。
 */
@Service
public class BusinessWorkspaceService {
    private static final OperatorContext SYSTEM_CONTEXT = new OperatorContext("system-workspace", RoleCode.ADMIN, DataScope.ALL);
    private final BusinessWorkspaceRepository demoRepository;
    private final FormalBusinessWorkspaceQuery formalQuery;
    private final boolean demoEnabled;
    private final BusinessWorkspaceDataScopeFilter demoScopeFilter = new BusinessWorkspaceDataScopeFilter();

    public BusinessWorkspaceService(BusinessWorkspaceRepository repository) {
        this(repository, null, null, null, null, false);
    }

    public BusinessWorkspaceService(BusinessWorkspaceRepository repository, BusinessDocumentRepository documents) {
        this(repository, documents, documents == null ? null : new ProgressBoardService(documents), null, null, false);
    }

    public BusinessWorkspaceService(BusinessWorkspaceRepository repository, BusinessDocumentRepository documents,
                                    ProgressBoardService progress) {
        this(repository, documents, progress, null, null, false);
    }

    @Autowired
    public BusinessWorkspaceService(BusinessWorkspaceRepository repository, BusinessDocumentRepository documents,
                                    ProgressBoardService progress, StockLedgerService stocks, ApprovalService approvals,
                                    @Value("${zhubao.business.workspace.demo-enabled:false}") boolean demoEnabled) {
        this.demoRepository = repository;
        this.formalQuery = new FormalBusinessWorkspaceQuery(documents, progress, stocks, approvals);
        this.demoEnabled = demoEnabled;
    }

    public InboundWorkspaceData inbound() { return inbound(SYSTEM_CONTEXT); }
    public InboundWorkspaceData inbound(OperatorContext operator) {
        return demoEnabled ? demoScopeFilter.filter(demoRepository.inbound(), operator) : formalQuery.inbound(operator);
    }
    public ProductionWorkspaceData production() { return production(SYSTEM_CONTEXT); }
    public ProductionWorkspaceData production(OperatorContext operator) {
        return demoEnabled ? demoScopeFilter.filter(demoRepository.production(), operator) : formalQuery.production(operator);
    }
    public QualityWorkspaceData quality() { return quality(SYSTEM_CONTEXT); }
    public QualityWorkspaceData quality(OperatorContext operator) {
        return demoEnabled ? demoScopeFilter.filter(demoRepository.quality(), operator) : formalQuery.quality(operator);
    }
    public InventoryWorkspaceData inventory() { return inventory(SYSTEM_CONTEXT); }
    public InventoryWorkspaceData inventory(OperatorContext operator) {
        return demoEnabled ? demoScopeFilter.filter(demoRepository.inventory(), operator) : formalQuery.inventory(operator);
    }
    public MobileWorkspaceData mobile() { return mobile(SYSTEM_CONTEXT); }
    public MobileWorkspaceData mobile(OperatorContext operator) {
        return demoEnabled ? demoScopeFilter.filter(demoRepository.mobile(), operator) : formalQuery.mobile(operator);
    }
    public BusinessWorkspaceSummary summary() { return summary(SYSTEM_CONTEXT); }
    public BusinessWorkspaceSummary summary(OperatorContext operator) {
        return new BusinessWorkspaceSummary(inbound(operator).orders().size(), production(operator).workOrders().size(),
                quality(operator).cases().size(), inventory(operator).ledgerEntries().size(), mobile(operator).tasks().size(),
                demoEnabled ? "DEMO" : "LIVE");
    }
}
