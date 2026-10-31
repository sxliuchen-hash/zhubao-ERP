package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkOperationReport;
import com.zhubao.erp.goldwork.domain.GoldworkOperationRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkQualityDecision;
import com.zhubao.erp.goldwork.domain.GoldworkQualityOutcome;
import com.zhubao.erp.goldwork.domain.GoldworkQualityRepository;
import com.zhubao.erp.goldwork.domain.GoldworkQualityView;
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
 * 按报工份额记录放行、返工或待核。不套珠料默认，不改成色和收费。
 */
@Service
public class GoldworkQualityService {

    private final GoldworkOrderRepository orders;
    private final GoldworkOperationRepository reports;
    private final GoldworkQualityRepository qualities;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkQualityService(GoldworkOrderRepository orders, GoldworkOperationRepository reports,
                                  GoldworkQualityRepository qualities, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.reports = reports;
        this.qualities = qualities;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkQualityView> list(OperatorContext actor, String goldworkCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return qualities.list(order.goldworkCode()).stream().map(this::view).toList();
    }

    public GoldworkQualityView decide(String requestId, OperatorContext actor, String goldworkCode, String reportId,
                                      String priorQualityId, String outcome, BigDecimal pieceCount, BigDecimal netWeight,
                                      String weightUnit, String purity, String note, boolean useBeadDefault,
                                      boolean changePurity, boolean changeFee) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        if (useBeadDefault) {
            throw new BusinessException("不使用珠料质检默认条件");
        }
        if (changePurity || differs(purity, reportPurity(actor, goldworkCode, reportId))) {
            throw new BusinessException("返工不改成色");
        }
        if (changeFee) {
            throw new BusinessException("返工不改收费");
        }
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String payload = order.goldworkCode() + "|" + mark(reportId) + "|" + mark(priorQualityId) + "|" + mark(outcome)
                + "|" + plain(pieceCount) + "|" + plain(netWeight) + "|" + mark(weightUnit) + "|" + mark(note);
        String qualityId = unit.execute(new CommandCall(
                requestId, actor.operator(), "DECIDE_GOLDWORK_QUALITY", "GOLDWORK_QUALITY", order.goldworkCode(), payload
        ), session -> write(session, order, reportId, priorQualityId, outcome, pieceCount, netWeight, weightUnit, note));
        return view(qualities.find(qualityId).orElseThrow(() -> new BusinessException("质量记录不存在")));
    }

    private String reportPurity(OperatorContext actor, String goldworkCode, String reportId) {
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return requireReport(order, reportId).purity();
    }

    private String write(WorkSession session, GoldworkOrder order, String reportId, String priorQualityId, String outcomeText,
                         BigDecimal pieceCount, BigDecimal netWeight, String weightUnit, String note) {
        GoldworkOperationReport report = requireReport(order, reportId);
        GoldworkQualityOutcome outcome = parseOutcome(outcomeText);
        String priorId = text(priorQualityId);
        int round = 1;
        if (!priorId.isEmpty()) {
            GoldworkQualityDecision prior = qualities.find(priorId).orElseThrow(() -> new BusinessException("原轮次不存在"));
            if (!prior.goldworkCode().equals(order.goldworkCode()) || !prior.reportId().equals(report.reportId())) {
                throw new BusinessException("返工只影响本份额");
            }
            round = prior.roundNo() + 1;
        }
        String qualityId = "GQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        qualities.insert(new GoldworkQualityDecision(
                qualityId, order.goldworkCode(), report.reportId(), priorId, round, outcome,
                pieceCount, netWeight, weightUnit, report.purity(), note
        ), session);
        return qualityId;
    }

    private GoldworkOperationReport requireReport(GoldworkOrder order, String reportId) {
        GoldworkOperationReport report = reports.find(text(reportId)).orElseThrow(() -> new BusinessException("报工不存在"));
        if (!report.goldworkCode().equals(order.goldworkCode())) {
            throw new BusinessException("报工不属于这张金工单");
        }
        return report;
    }

    private static boolean differs(String requested, String actual) {
        String left = text(requested);
        return !left.isEmpty() && !left.equals(actual);
    }

    private static GoldworkQualityOutcome parseOutcome(String outcomeText) {
        try {
            return GoldworkQualityOutcome.valueOf(text(outcomeText));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("请选择放行、返工或待核");
        }
    }

    private GoldworkQualityView view(GoldworkQualityDecision decision) {
        return new GoldworkQualityView(
                decision.qualityId(), decision.goldworkCode(), decision.reportId(), decision.priorQualityId(),
                decision.roundNo(), decision.outcome().name(), decision.pieceCount(), decision.netWeight(),
                decision.weightUnit(), decision.purity(), decision.note(), false, false
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
