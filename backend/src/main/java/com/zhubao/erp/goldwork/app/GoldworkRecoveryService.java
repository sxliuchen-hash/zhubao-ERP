package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLine;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMeasurement;
import com.zhubao.erp.goldwork.domain.GoldworkMovement;
import com.zhubao.erp.goldwork.domain.GoldworkMovementKind;
import com.zhubao.erp.goldwork.domain.GoldworkMovementRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkRecovery;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryKind;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryRepository;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryView;
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
 * 记录回收、余料、实物报废和不可恢复损耗。不并进成品，不折抵费用，不把未知重量写成 0。
 */
@Service
public class GoldworkRecoveryService {

    private final GoldworkOrderRepository orders;
    private final GoldworkMaterialRepository materials;
    private final GoldworkMovementRepository movements;
    private final GoldworkRecoveryRepository recoveries;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkRecoveryService(GoldworkOrderRepository orders, GoldworkMaterialRepository materials,
                                   GoldworkMovementRepository movements, GoldworkRecoveryRepository recoveries,
                                   BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.materials = materials;
        this.movements = movements;
        this.recoveries = recoveries;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkRecoveryView> list(OperatorContext actor, String goldworkCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return recoveries.list(order.goldworkCode()).stream().map(this::view).toList();
    }

    public GoldworkRecoveryView record(String requestId, OperatorContext actor, String goldworkCode, String sourceLineId,
                                       String outputMovementId, String kind, BigDecimal pieceCount, BigDecimal netWeight,
                                       BigDecimal grossWeight, String weightUnit, String purity, boolean clearAsLoss,
                                       boolean addToFinished, BigDecimal feeOffset, boolean autoReturn, boolean inferWeight) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        refusePending(clearAsLoss, addToFinished, feeOffset, autoReturn, inferWeight);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String payload = order.goldworkCode() + "|" + mark(sourceLineId) + "|" + mark(outputMovementId) + "|" + mark(kind)
                + "|" + plain(pieceCount) + "|" + plain(netWeight) + "|" + plain(grossWeight) + "|" + mark(weightUnit)
                + "|" + mark(purity);
        String recoveryId = unit.execute(new CommandCall(
                requestId, actor.operator(), "RECORD_GOLDWORK_RECOVERY", "GOLDWORK_RECOVERY", order.goldworkCode(), payload
        ), session -> write(session, order, sourceLineId, outputMovementId, kind, pieceCount, netWeight, grossWeight,
                weightUnit, purity));
        return view(recoveries.find(recoveryId).orElseThrow(() -> new BusinessException("回收记录不存在")));
    }

    /** 成品产出和回收各自保留，调用方不能把两边相加后入账。 */
    public void refuseAddToFinished(String recoveryId, String outputMovementId) {
        GoldworkRecovery recovery = recoveries.find(recoveryId).orElseThrow(() -> new BusinessException("回收记录不存在"));
        GoldworkMovement output = movements.find(outputMovementId).orElseThrow(() -> new BusinessException("成品产出不存在"));
        if (!recovery.outputMovementId().equals(output.movementId())) {
            throw new BusinessException("回收料不能并进成品");
        }
        GoldworkMeasurement.refuseCombine(recovery.purity(), recovery.weightUnit(), output.purity(), output.weightUnit());
    }

    private String write(WorkSession session, GoldworkOrder order, String sourceLineId, String outputMovementId,
                         String kindText, BigDecimal pieceCount, BigDecimal netWeight, BigDecimal grossWeight,
                         String weightUnit, String purity) {
        GoldworkMaterialLine source = materials.find(text(sourceLineId))
                .orElseThrow(() -> new BusinessException("金料来源不存在"));
        if (!source.goldworkCode().equals(order.goldworkCode())) {
            throw new BusinessException("金料来源不属于这张金工单");
        }
        GoldworkMovement output = movements.find(text(outputMovementId))
                .orElseThrow(() -> new BusinessException("成品产出不存在"));
        if (!output.goldworkCode().equals(order.goldworkCode()) || output.kind() != GoldworkMovementKind.OUTPUT) {
            throw new BusinessException("回收只能对照已记录的产出，不能并进该产出");
        }
        GoldworkRecoveryKind kind = parseKind(kindText);
        String recoveryId = "GR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        recoveries.insert(new GoldworkRecovery(
                recoveryId, order.goldworkCode(), source.lineId(), output.movementId(), kind,
                GoldworkMeasurement.keepOwner(source.owner()),
                pieceCount, netWeight, grossWeight, weightUnit, purity, "UNDECIDED"
        ), session);
        return recoveryId;
    }

    private static void refusePending(boolean clearAsLoss, boolean addToFinished, BigDecimal feeOffset,
                                      boolean autoReturn, boolean inferWeight) {
        if (clearAsLoss) {
            throw new BusinessException("回收料不能当成损耗清零");
        }
        if (addToFinished) {
            throw new BusinessException("回收料不能并进成品");
        }
        if (feeOffset != null) {
            throw new BusinessException("回收料不能折抵客户费用");
        }
        if (autoReturn) {
            throw new BusinessException("回收返还规则尚未核定");
        }
        if (inferWeight) {
            throw new BusinessException("回收称重尚未核定，不能用短重补重量");
        }
    }

    private static GoldworkRecoveryKind parseKind(String kindText) {
        try {
            return GoldworkRecoveryKind.valueOf(text(kindText));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("请选择回收去向");
        }
    }

    private GoldworkRecoveryView view(GoldworkRecovery recovery) {
        return new GoldworkRecoveryView(
                recovery.recoveryId(), recovery.goldworkCode(), recovery.sourceLineId(), recovery.outputMovementId(),
                recovery.kind().name(), recovery.owner().name(), recovery.pieceCount(), recovery.netWeight(),
                recovery.grossWeight(), recovery.weightUnit(), recovery.purity(), recovery.precisionState(), false, false
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
