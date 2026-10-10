package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessWorkspaceRepository;
import com.zhubao.erp.business.domain.BusinessReviewData.BusinessWorkspaceSummary;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundOrder;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.ProgressRow;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BusinessWorkspaceService {

    private static final OperatorContext REVIEW_CONTEXT = new OperatorContext("system-review", RoleCode.ADMIN, DataScope.ALL);

    private final BusinessWorkspaceRepository businessWorkspaceRepository;
    private final BusinessDocumentRepository businessDocumentRepository;
    private final ProgressBoardService progressBoardService;
    private final BusinessWorkspaceDataScopeFilter dataScopeFilter = new BusinessWorkspaceDataScopeFilter();

    public BusinessWorkspaceService(BusinessWorkspaceRepository businessWorkspaceRepository) {
        this(businessWorkspaceRepository, null, null);
    }

    public BusinessWorkspaceService(
            BusinessWorkspaceRepository businessWorkspaceRepository,
            BusinessDocumentRepository businessDocumentRepository
    ) {
        this(businessWorkspaceRepository, businessDocumentRepository, null);
    }

    @Autowired
    public BusinessWorkspaceService(
            BusinessWorkspaceRepository businessWorkspaceRepository,
            BusinessDocumentRepository businessDocumentRepository,
            ProgressBoardService progressBoardService
    ) {
        this.businessWorkspaceRepository = businessWorkspaceRepository;
        this.businessDocumentRepository = businessDocumentRepository;
        this.progressBoardService = progressBoardService;
    }

    public InboundWorkspaceData inbound() {
        return inbound(REVIEW_CONTEXT);
    }

    public InboundWorkspaceData inbound(OperatorContext operatorContext) {
        return dataScopeFilter.filter(mergeLiveMotherOrders(businessWorkspaceRepository.inbound()), operatorContext);
    }

    private InboundWorkspaceData mergeLiveMotherOrders(InboundWorkspaceData snapshot) {
        if (businessDocumentRepository == null) {
            return snapshot;
        }
        List<InboundOrder> merged = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (var record : businessDocumentRepository.listMotherOrders()) {
            if (codes.add(record.code())) {
                merged.add(toInboundOrder(record));
            }
        }
        for (InboundOrder order : snapshot.orders()) {
            if (codes.add(order.code())) {
                merged.add(order);
            }
        }
        return new InboundWorkspaceData(snapshot.checkpoints(), List.copyOf(merged), snapshot.risks());
    }

    private ProductionWorkspaceData mergeLiveWorkOrders(ProductionWorkspaceData snapshot) {
        if (progressBoardService == null) {
            return snapshot;
        }
        List<com.zhubao.erp.business.domain.BusinessReviewData.WorkOrder> merged = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (ProgressRow row : progressBoardService.list(REVIEW_CONTEXT, "", "", "", "")) {
            if ("inbound".equals(row.openTab()) || !codes.add(row.workOrderCode())) {
                continue;
            }
            merged.add(new com.zhubao.erp.business.domain.BusinessReviewData.WorkOrder(
                    row.workOrderCode(),
                    row.customerName(),
                    row.motherOrderCode(),
                    row.currentProcess(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    row.status(),
                    "",
                    "",
                    0,
                    0,
                    row.status(),
                    0
            ));
        }
        for (var order : snapshot.workOrders()) {
            if (codes.add(order.code())) {
                merged.add(order);
            }
        }
        return new ProductionWorkspaceData(snapshot.stationLoads(), List.copyOf(merged), snapshot.handoffs());
    }

    private QualityWorkspaceData mergeLiveQualityCases(QualityWorkspaceData snapshot) {
        if (progressBoardService == null) {
            return snapshot;
        }
        List<com.zhubao.erp.business.domain.BusinessReviewData.QualityCase> merged = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (ProgressRow row : progressBoardService.list(REVIEW_CONTEXT, "", "", "", "")) {
            if (ProgressBoardService.NO_QUALITY.equals(row.qualityOutcome()) || !codes.add(row.workOrderCode())) {
                continue;
            }
            merged.add(new com.zhubao.erp.business.domain.BusinessReviewData.QualityCase(
                    row.workOrderCode(),
                    row.workOrderCode(),
                    row.currentProcess(),
                    "",
                    "",
                    row.qualityOutcome(),
                    row.status()
            ));
        }
        for (var qualityCase : snapshot.cases()) {
            if (codes.add(qualityCase.workOrder())) {
                merged.add(qualityCase);
            }
        }
        return new QualityWorkspaceData(snapshot.metrics(), List.copyOf(merged), snapshot.lossApprovals());
    }

    private InboundOrder toInboundOrder(com.zhubao.erp.business.domain.MotherOrderRecord record) {
        String customer = record.customerName() == null || record.customerName().isBlank()
                ? "客户#" + record.customerId()
                : record.customerName();
        String goodsName = record.goodsName() == null ? "" : record.goodsName();
        String weight = record.weight() == null ? "" : record.weight().stripTrailingZeros().toPlainString() + " g";
        int quantity = record.quantity() == null ? 0 : record.quantity().intValue();
        return new InboundOrder(
                record.code(),
                customer,
                goodsName.isBlank() ? record.materialCode() : goodsName,
                record.inboundDate() == null ? "" : record.inboundDate(),
                record.expressCompany() == null ? "" : record.expressCompany(),
                record.courierNo() == null ? "" : record.courierNo(),
                record.senderName() == null ? "" : record.senderName(),
                record.receiverName() == null ? "" : record.receiverName(),
                record.unpackSigner() == null ? "" : record.unpackSigner(),
                goodsName,
                quantity,
                weight,
                "已入库",
                "待补",
                "客户所有",
                "加工要求：" + record.processingRequirement() + "；期限：" + record.dueDate()
        );
    }

    public ProductionWorkspaceData production() {
        return production(REVIEW_CONTEXT);
    }

    public ProductionWorkspaceData production(OperatorContext operatorContext) {
        return dataScopeFilter.filter(mergeLiveWorkOrders(businessWorkspaceRepository.production()), operatorContext);
    }

    public QualityWorkspaceData quality() {
        return quality(REVIEW_CONTEXT);
    }

    public QualityWorkspaceData quality(OperatorContext operatorContext) {
        return dataScopeFilter.filter(mergeLiveQualityCases(businessWorkspaceRepository.quality()), operatorContext);
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
