package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLine;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMeasurement;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkSupplement;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementDirection;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementRepository;
import com.zhubao.erp.goldwork.domain.GoldworkSupplementView;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
import com.zhubao.erp.inventory.domain.StockDocumentRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 客户补料和公司补料各记独立来源。不覆盖最初来料，不在旧行上加数，也不另记补料费。
 */
@Service
public class GoldworkSupplementService {

    private final GoldworkOrderRepository orders;
    private final GoldworkMaterialRepository materials;
    private final GoldworkSupplementRepository supplements;
    private final CompanyMaterialReceiptRepository companyReceipts;
    private final StockDocumentRepository documents;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkSupplementService(GoldworkOrderRepository orders, GoldworkMaterialRepository materials,
                                     GoldworkSupplementRepository supplements,
                                     CompanyMaterialReceiptRepository companyReceipts, StockDocumentRepository documents,
                                     BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.materials = materials;
        this.supplements = supplements;
        this.companyReceipts = companyReceipts;
        this.documents = documents;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkSupplementView> list(OperatorContext actor, String goldworkCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return supplements.list(order.goldworkCode()).stream().map(this::view).toList();
    }

    public GoldworkSupplementView issue(String requestId, OperatorContext actor, String goldworkCode, String originalLineId,
                                        String sourceId, Integer sourceLineNo, String purpose, BigDecimal pieceCount,
                                        BigDecimal netWeight, BigDecimal grossWeight, String weightUnit, String purity,
                                        boolean addOntoOriginal, boolean changeOwner, BigDecimal feeAmount) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        refusePending(addOntoOriginal, changeOwner, feeAmount);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String payload = "ISSUE|" + order.goldworkCode() + "|" + mark(originalLineId) + "|" + mark(sourceId) + "|"
                + (sourceLineNo == null ? "" : sourceLineNo) + "|" + mark(purpose) + "|" + plain(pieceCount) + "|"
                + plain(netWeight) + "|" + plain(grossWeight) + "|" + mark(weightUnit) + "|" + mark(purity);
        String supplementId = unit.execute(new CommandCall(
                requestId, actor.operator(), "ISSUE_GOLDWORK_SUPPLEMENT", "GOLDWORK_SUPPLEMENT", order.goldworkCode(), payload
        ), session -> writeIssue(session, order, originalLineId, sourceId, sourceLineNo, purpose, pieceCount, netWeight,
                grossWeight, weightUnit, purity));
        return view(supplements.find(supplementId).orElseThrow(() -> new BusinessException("补料不存在")));
    }

    public GoldworkSupplementView giveBack(String requestId, OperatorContext actor, String goldworkCode,
                                           String originalLineId, String priorSupplementId, String purpose,
                                           BigDecimal pieceCount, BigDecimal netWeight, BigDecimal grossWeight,
                                           String weightUnit, String purity, boolean addOntoOriginal, boolean changeOwner,
                                           BigDecimal feeAmount) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        refusePending(addOntoOriginal, changeOwner, feeAmount);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String payload = "RETURN|" + order.goldworkCode() + "|" + mark(originalLineId) + "|" + mark(priorSupplementId)
                + "|" + mark(purpose) + "|" + plain(pieceCount) + "|" + plain(netWeight) + "|" + plain(grossWeight)
                + "|" + mark(weightUnit) + "|" + mark(purity);
        String supplementId = unit.execute(new CommandCall(
                requestId, actor.operator(), "RETURN_GOLDWORK_SUPPLEMENT", "GOLDWORK_SUPPLEMENT", order.goldworkCode(), payload
        ), session -> writeReturn(session, order, originalLineId, priorSupplementId, purpose, pieceCount, netWeight,
                grossWeight, weightUnit, purity));
        return view(supplements.find(supplementId).orElseThrow(() -> new BusinessException("补料不存在")));
    }

    private String writeIssue(WorkSession session, GoldworkOrder order, String originalLineId, String sourceId,
                              Integer sourceLineNo, String purpose, BigDecimal pieceCount, BigDecimal netWeight,
                              BigDecimal grossWeight, String weightUnit, String purity) {
        GoldworkMaterialLine original = requireOriginal(order, originalLineId);
        ResolvedSource source = resolve(session, order, sourceId, sourceLineNo);
        return insert(session, order, original.lineId(), "", GoldworkSupplementDirection.ISSUE, source.owner(),
                source.sourceId(), source.lineNo(), purpose, pieceCount, netWeight, grossWeight, weightUnit, purity);
    }

    private String writeReturn(WorkSession session, GoldworkOrder order, String originalLineId, String priorSupplementId,
                               String purpose, BigDecimal pieceCount, BigDecimal netWeight, BigDecimal grossWeight,
                               String weightUnit, String purity) {
        GoldworkMaterialLine original = requireOriginal(order, originalLineId);
        String priorId = text(priorSupplementId);
        if (priorId.equals(original.lineId())) {
            throw new BusinessException("退补不能写回最初来料");
        }
        GoldworkSupplement prior = supplements.find(priorId).orElseThrow(() -> new BusinessException("补料不存在"));
        if (!prior.goldworkCode().equals(order.goldworkCode()) || prior.direction() != GoldworkSupplementDirection.ISSUE
                || !prior.originalLineId().equals(original.lineId())) {
            throw new BusinessException("退补必须指向一笔补料");
        }
        return insert(session, order, original.lineId(), prior.supplementId(), GoldworkSupplementDirection.RETURN,
                GoldworkMeasurement.keepOwner(prior.owner()), prior.sourceId(), prior.sourceLineNo(), purpose,
                pieceCount, netWeight, grossWeight, weightUnit, purity);
    }

    private String insert(WorkSession session, GoldworkOrder order, String originalLineId, String priorSupplementId,
                          GoldworkSupplementDirection direction, OwnerType owner, String sourceId, Integer sourceLineNo,
                          String purpose, BigDecimal pieceCount, BigDecimal netWeight, BigDecimal grossWeight,
                          String weightUnit, String purity) {
        String supplementId = "GS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        supplements.insert(new GoldworkSupplement(
                supplementId, order.goldworkCode(), originalLineId, priorSupplementId, direction,
                GoldworkMeasurement.keepOwner(owner), sourceId, sourceLineNo, purpose,
                pieceCount, netWeight, grossWeight, weightUnit, purity, "UNDECIDED"
        ), session);
        return supplementId;
    }

    private ResolvedSource resolve(WorkSession session, GoldworkOrder order, String sourceId, Integer sourceLineNo) {
        String source = text(sourceId);
        if (sourceLineNo == null) {
            CompanyMaterialReceipt receipt = companyReceipts.find(source).orElseThrow(() -> new BusinessException("公司收货不存在"));
            return new ResolvedSource(OwnerType.COMPANY, receipt.receiptId(), null);
        }
        if (sourceLineNo < 1) {
            throw new BusinessException("请选择收货明细");
        }
        StockDocument document = documents.lock(source, session).orElseThrow(() -> new BusinessException("客户收货不存在"));
        if (!StockDocumentKinds.PREP_RECEIPT.equals(document.kind()) || !StockDocumentKinds.POSTED.equals(document.status())
                || !OwnerType.CUSTOMER.name().equals(document.ownerType())
                || document.customerId() == null || document.customerId() != order.customerId()) {
            throw new BusinessException("来源不是该客户的来料");
        }
        int expected = sourceLineNo;
        StockDocumentLine line = document.lines().stream().filter(item -> item.lineNo() == expected).findFirst()
                .orElseThrow(() -> new BusinessException("收货明细不存在"));
        if (!line.counted() || line.batchCode().isBlank()) {
            throw new BusinessException("来源尚未入账，不能引用");
        }
        return new ResolvedSource(OwnerType.CUSTOMER, document.documentCode(), line.lineNo());
    }

    private GoldworkMaterialLine requireOriginal(GoldworkOrder order, String originalLineId) {
        GoldworkMaterialLine original = materials.find(text(originalLineId))
                .orElseThrow(() -> new BusinessException("最初来料不存在"));
        if (!original.goldworkCode().equals(order.goldworkCode())) {
            throw new BusinessException("最初来料不属于这张金工单");
        }
        return original;
    }

    private static void refusePending(boolean addOntoOriginal, boolean changeOwner, BigDecimal feeAmount) {
        if (addOntoOriginal) {
            throw new BusinessException("不能在旧来源上直接加数");
        }
        if (changeOwner) {
            throw new BusinessException("不能静默改所有权");
        }
        if (feeAmount != null) {
            throw new BusinessException("补料是否另收费尚未核定");
        }
    }

    private GoldworkSupplementView view(GoldworkSupplement supplement) {
        return new GoldworkSupplementView(
                supplement.supplementId(), supplement.goldworkCode(), supplement.originalLineId(),
                supplement.priorSupplementId(), supplement.direction().name(), supplement.owner().name(),
                supplement.sourceId(), supplement.sourceLineNo(), supplement.purpose(), supplement.pieceCount(),
                supplement.netWeight(), supplement.grossWeight(), supplement.weightUnit(), supplement.purity(),
                supplement.precisionState(), false, false
        );
    }

    private GoldworkOrder requireVisible(OperatorContext actor, String goldworkCode) {
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE
                && (actor.customerScope() == null || actor.customerScope().isEmpty())) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
        GoldworkOrder order = orders.find(goldworkCode == null ? "" : goldworkCode.trim())
                .orElseThrow(() -> new BusinessException("金工单不存在"));
        if (!CustomerScopeRules.allowed(actor, order.customerId(), order.customerName())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
        return order;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String mark(String value) {
        return text(value).replace('|', '/');
    }

    private record ResolvedSource(OwnerType owner, String sourceId, Integer lineNo) {
    }
}
