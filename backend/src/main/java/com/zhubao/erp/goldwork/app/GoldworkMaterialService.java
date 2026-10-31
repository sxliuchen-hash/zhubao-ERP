package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialIntent;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLine;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLineView;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMeasurement;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
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
 * 记录金料来源和申报计量。精度未核定，不写入库存账，也不把空值写成 0。
 */
@Service
public class GoldworkMaterialService {

    private final GoldworkOrderRepository orders;
    private final GoldworkMaterialRepository lines;
    private final CompanyMaterialReceiptRepository companyReceipts;
    private final StockDocumentRepository documents;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkMaterialService(GoldworkOrderRepository orders, GoldworkMaterialRepository lines,
                                   CompanyMaterialReceiptRepository companyReceipts, StockDocumentRepository documents,
                                   BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.lines = lines;
        this.companyReceipts = companyReceipts;
        this.documents = documents;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkMaterialLineView> list(OperatorContext actor, String goldworkCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return lines.list(order.goldworkCode()).stream().map(this::view).toList();
    }

    public GoldworkMaterialLineView receive(String requestId, OperatorContext actor, String goldworkCode, String sourceId,
                                            Integer sourceLineNo, BigDecimal pieceCount, BigDecimal netWeight,
                                            BigDecimal grossWeight, String weightUnit, String purity) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String payload = order.goldworkCode() + "|" + mark(sourceId) + "|" + (sourceLineNo == null ? "" : sourceLineNo)
                + "|" + plain(pieceCount) + "|" + plain(netWeight) + "|" + plain(grossWeight) + "|"
                + mark(weightUnit) + "|" + mark(purity);
        String lineId = unit.execute(new CommandCall(
                requestId, actor.operator(), "RECEIVE_GOLDWORK_MATERIAL", "GOLDWORK_MATERIAL", order.goldworkCode(), payload
        ), session -> write(session, order, sourceId, sourceLineNo, pieceCount, netWeight, grossWeight, weightUnit, purity));
        return view(lines.find(lineId).orElseThrow(() -> new BusinessException("金料明细不存在")));
    }

    /** 精度未核定，不允许把两笔申报相加后入账。 */
    public void refuseCombine(String leftLineId, String rightLineId) {
        GoldworkMaterialLine left = lines.find(leftLineId).orElseThrow(() -> new BusinessException("金料明细不存在"));
        GoldworkMaterialLine right = lines.find(rightLineId).orElseThrow(() -> new BusinessException("金料明细不存在"));
        GoldworkMeasurement.refuseCombine(left.purity(), left.weightUnit(), right.purity(), right.weightUnit());
    }

    private String write(WorkSession session, GoldworkOrder order, String sourceId, Integer sourceLineNo,
                         BigDecimal pieceCount, BigDecimal netWeight, BigDecimal grossWeight, String weightUnit, String purity) {
        OwnerType owner = order.materialIntent() == GoldworkMaterialIntent.COMPANY_PREPARED
                ? OwnerType.COMPANY : OwnerType.CUSTOMER;
        String source = text(sourceId);
        Integer lineNo = null;
        if (owner == OwnerType.COMPANY) {
            if (sourceLineNo != null) {
                throw new BusinessException("公司自备按收货批次引用，不另记客户明细");
            }
            CompanyMaterialReceipt receipt = companyReceipts.find(source).orElseThrow(() -> new BusinessException("公司收货不存在"));
            source = receipt.receiptId();
        } else {
            if (sourceLineNo == null || sourceLineNo < 1) {
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
            lineNo = line.lineNo();
            source = document.documentCode();
        }
        String lineId = "GL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        lines.insert(new GoldworkMaterialLine(
                lineId, order.goldworkCode(), GoldworkMeasurement.keepOwner(owner), source, lineNo,
                pieceCount, netWeight, grossWeight, weightUnit, purity, "UNDECIDED"
        ), session);
        return lineId;
    }

    private GoldworkMaterialLineView view(GoldworkMaterialLine line) {
        return new GoldworkMaterialLineView(
                line.lineId(), line.goldworkCode(), line.owner().name(), line.sourceId(), line.sourceLineNo(),
                line.pieceCount(), line.netWeight(), line.grossWeight(), line.weightUnit(), line.purity(),
                line.precisionState(), false
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
}
