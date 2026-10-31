package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.goldwork.domain.GoldworkOperationReport;
import com.zhubao.erp.goldwork.domain.GoldworkOperationRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOperationView;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 按金工路线报工。必须有本单照片。不写库存，不生成工资，也不结束在制。
 */
@Service
public class GoldworkOperationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final GoldworkOrderRepository orders;
    private final GoldworkOperationRepository reports;
    private final DocumentManagementRepository documents;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkOperationService(GoldworkOrderRepository orders, GoldworkOperationRepository reports,
                                    DocumentManagementRepository documents, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.reports = reports;
        this.documents = documents;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkOperationView> list(OperatorContext actor, String goldworkCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return reports.list(order.goldworkCode()).stream().map(this::view).toList();
    }

    public GoldworkOperationView report(String requestId, OperatorContext actor, String goldworkCode, String stepId,
                                        BigDecimal pieceCount, BigDecimal netWeight, String weightUnit, String purity,
                                        String attachmentCode, boolean settleWage, boolean postStock, boolean closeWip) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        if (settleWage) {
            throw new BusinessException("报工不生成工资");
        }
        if (postStock) {
            throw new BusinessException("报工不写库存");
        }
        if (closeWip) {
            throw new BusinessException("报工不结束在制");
        }
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String photo = text(attachmentCode);
        if (photo.isEmpty()) {
            throw new BusinessException("没有有效的现场照片");
        }
        String payload = order.goldworkCode() + "|" + mark(stepId) + "|" + plain(pieceCount) + "|" + plain(netWeight)
                + "|" + mark(weightUnit) + "|" + mark(purity) + "|" + photo;
        String reportId = unit.execute(new CommandCall(
                requestId, actor.operator(), "REPORT_GOLDWORK_OPERATION", "GOLDWORK_OPERATION", order.goldworkCode(), payload
        ), session -> write(session, actor, order, stepId, pieceCount, netWeight, weightUnit, purity, photo));
        return view(reports.find(reportId).orElseThrow(() -> new BusinessException("报工不存在")));
    }

    private String write(WorkSession session, OperatorContext actor, GoldworkOrder order, String stepId,
                         BigDecimal pieceCount, BigDecimal netWeight, String weightUnit, String purity, String attachmentCode) {
        ProcessStep step = goldStep(order, stepId);
        EvidenceAttachment attachment = documents.findAttachmentByCode(attachmentCode).orElse(null);
        assertPhoto(attachment, order.goldworkCode());
        if (reports.photoUsed(attachment.code(), session)) {
            throw new BusinessException("这张照片已经用于其他业务，不能当作本次拍摄");
        }
        String reportId = "GO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        reports.insert(new GoldworkOperationReport(
                reportId, order.goldworkCode(), step.id(), pieceCount, netWeight, weightUnit, purity,
                attachment.code(), actor.operator(), LocalDate.now(ZONE).toString(), "UNDECIDED"
        ), session);
        return reportId;
    }

    private static ProcessStep goldStep(GoldworkOrder order, String stepId) {
        String id = text(stepId);
        ProcessStep step;
        try {
            step = ProcessCatalog.require(id);
        } catch (BusinessException ex) {
            throw new BusinessException("金工不使用珠料工序");
        }
        if (step.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("金工不使用珠料工序");
        }
        if (order.route().stream().noneMatch(item -> item.id().equals(step.id()))) {
            throw new BusinessException("工序不在本金工路线上");
        }
        return step;
    }

    private static void assertPhoto(EvidenceAttachment attachment, String goldworkCode) {
        if (attachment == null || attachment.status() == null || attachment.status().isBlank()
                || "已下线".equals(attachment.status()) || "待核对".equals(attachment.status())) {
            throw new BusinessException("没有有效的现场照片");
        }
        if (attachment.objectType() == null || attachment.objectType().isBlank()
                || EvidenceCodes.HISTORICAL.equals(attachment.objectType())) {
            throw new BusinessException("历史照片不能当作本次拍摄");
        }
        if (!goldworkCode.equals(attachment.businessCode()) || !"GOLDWORK".equals(attachment.objectType())) {
            throw new BusinessException("照片不属于当前金工单");
        }
        String media = attachment.mediaType() == null ? "" : attachment.mediaType().toLowerCase(Locale.ROOT);
        if (!"image/jpeg".equals(media) && !"image/png".equals(media)) {
            throw new BusinessException("报工照片必须是可用图片");
        }
    }

    private GoldworkOperationView view(GoldworkOperationReport report) {
        ProcessStep step = ProcessCatalog.require(report.stepId());
        return new GoldworkOperationView(
                report.reportId(), report.goldworkCode(), report.stepId(), step.name(),
                report.pieceCount(), report.netWeight(), report.weightUnit(), report.purity(),
                report.attachmentCode(), report.workerName(), report.reportedOn(), report.precisionState(), false, false
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
