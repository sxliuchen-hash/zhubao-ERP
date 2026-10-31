package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLine;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMeasurement;
import com.zhubao.erp.goldwork.domain.GoldworkMovement;
import com.zhubao.erp.goldwork.domain.GoldworkMovementKind;
import com.zhubao.erp.goldwork.domain.GoldworkMovementRepository;
import com.zhubao.erp.goldwork.domain.GoldworkMovementView;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 按金工工序记录投入、产出、在制和明确处置。不推算损耗，不把毛重写成净金。
 */
@Service
public class GoldworkMovementService {

    private static final Set<String> NAMED_DISPOSALS = Set.of("RETURN", "PHYSICAL_SCRAP", "HOLD");

    private final GoldworkOrderRepository orders;
    private final GoldworkMaterialRepository materials;
    private final GoldworkMovementRepository movements;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkMovementService(GoldworkOrderRepository orders, GoldworkMaterialRepository materials,
                                   GoldworkMovementRepository movements, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.materials = materials;
        this.movements = movements;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkMovementView> list(OperatorContext actor, String goldworkCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return movements.list(order.goldworkCode()).stream().map(this::view).toList();
    }

    public GoldworkMovementView record(String requestId, OperatorContext actor, String goldworkCode, String stepId,
                                       String kind, String sourceLineId, BigDecimal pieceCount, BigDecimal netWeight,
                                       BigDecimal grossWeight, String weightUnit, String purity, String disposition,
                                       String conversionNote, boolean useGrossAsNet, boolean inferShortage,
                                       BigDecimal conversionFactor, boolean enforcePieceConservation) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        refuseUndecidedAlgorithms(useGrossAsNet, inferShortage, conversionFactor, enforcePieceConservation);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String payload = order.goldworkCode() + "|" + mark(stepId) + "|" + mark(kind) + "|" + mark(sourceLineId)
                + "|" + plain(pieceCount) + "|" + plain(netWeight) + "|" + plain(grossWeight) + "|" + mark(weightUnit)
                + "|" + mark(purity) + "|" + mark(disposition) + "|" + mark(conversionNote);
        String movementId = unit.execute(new CommandCall(
                requestId, actor.operator(), "RECORD_GOLDWORK_MOVEMENT", "GOLDWORK_MOVEMENT", order.goldworkCode(), payload
        ), session -> write(session, order, stepId, kind, sourceLineId, pieceCount, netWeight, grossWeight,
                weightUnit, purity, disposition, conversionNote));
        return view(movements.find(movementId).orElseThrow(() -> new BusinessException("投入产出不存在")));
    }

    private String write(WorkSession session, GoldworkOrder order, String stepId, String kindText, String sourceLineId,
                         BigDecimal pieceCount, BigDecimal netWeight, BigDecimal grossWeight, String weightUnit,
                         String purity, String disposition, String conversionNote) {
        String step = text(stepId);
        if (order.route().stream().noneMatch(item -> item.id().equals(step))) {
            throw new BusinessException("工序不在本金工路线上");
        }
        GoldworkMovementKind kind = parseKind(kindText);
        GoldworkMaterialLine source = materials.find(text(sourceLineId))
                .orElseThrow(() -> new BusinessException("金料来源不存在"));
        if (!source.goldworkCode().equals(order.goldworkCode())) {
            throw new BusinessException("金料来源不属于这张金工单");
        }
        String named = text(disposition);
        String note = text(conversionNote);
        if (kind == GoldworkMovementKind.DISPOSAL) {
            if ("LOSS".equals(named)) {
                throw new BusinessException("不能以短重推定责任或赔偿");
            }
            if (!NAMED_DISPOSALS.contains(named)) {
                throw new BusinessException("请写明处置，不能把短重当成损耗");
            }
        } else if (!named.isEmpty()) {
            throw new BusinessException("投入和产出不写成处置");
        }
        String declaredPurity = text(purity);
        if (!declaredPurity.equals(source.purity()) && note.isEmpty()) {
            throw new BusinessException("成色变化必须写明已经发生的转换，不能按系数换算");
        }
        String movementId = "GM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        movements.insert(new GoldworkMovement(
                movementId, order.goldworkCode(), step, kind, source.lineId(),
                GoldworkMeasurement.keepOwner(source.owner()),
                pieceCount, netWeight, grossWeight, weightUnit, declaredPurity, named, note, "UNDECIDED"
        ), session);
        return movementId;
    }

    private static void refuseUndecidedAlgorithms(boolean useGrossAsNet, boolean inferShortage,
                                                  BigDecimal conversionFactor, boolean enforcePieceConservation) {
        if (useGrossAsNet) {
            throw new BusinessException("镶石成品总重不能直接当金料净重");
        }
        if (inferShortage) {
            throw new BusinessException("不能以短重推定责任或赔偿");
        }
        if (conversionFactor != null) {
            throw new BusinessException("金料转换关系尚未核定，不能按系数换算重量");
        }
        if (enforcePieceConservation) {
            throw new BusinessException("金工不按同颗数守恒");
        }
    }

    private static GoldworkMovementKind parseKind(String kindText) {
        String kind = text(kindText);
        try {
            return GoldworkMovementKind.valueOf(kind);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("请选择投入、产出、在制或处置");
        }
    }

    private GoldworkMovementView view(GoldworkMovement movement) {
        return new GoldworkMovementView(
                movement.movementId(), movement.goldworkCode(), movement.stepId(), movement.kind().name(),
                movement.sourceLineId(), movement.owner().name(), movement.pieceCount(), movement.netWeight(),
                movement.grossWeight(), movement.weightUnit(), movement.purity(), movement.disposition(),
                movement.conversionNote(), movement.precisionState(), false
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
