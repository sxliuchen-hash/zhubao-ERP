package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkDelivery;
import com.zhubao.erp.goldwork.domain.GoldworkDeliveryRepository;
import com.zhubao.erp.goldwork.domain.GoldworkDeliveryView;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkQualityDecision;
import com.zhubao.erp.goldwork.domain.GoldworkQualityOutcome;
import com.zhubao.erp.goldwork.domain.GoldworkQualityRepository;
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
 * 交付已放行的金工份额。不按克数乘金价，不写应收，也不写成普通发货。
 */
@Service
public class GoldworkDeliveryService {

    private final GoldworkOrderRepository orders;
    private final GoldworkQualityRepository qualities;
    private final GoldworkDeliveryRepository deliveries;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public GoldworkDeliveryService(GoldworkOrderRepository orders, GoldworkQualityRepository qualities,
                                   GoldworkDeliveryRepository deliveries, BusinessWorkUnit unit, PermissionGuard permissions) {
        this.orders = orders;
        this.qualities = qualities;
        this.deliveries = deliveries;
        this.unit = unit;
        this.permissions = permissions;
    }

    public List<GoldworkDeliveryView> list(OperatorContext actor, String goldworkCode) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_VIEW, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        return deliveries.list(order.goldworkCode()).stream().map(this::view).toList();
    }

    public GoldworkDeliveryView deliver(String requestId, OperatorContext actor, String goldworkCode, String qualityId,
                                        BigDecimal pieceCount, BigDecimal netWeight, String weightUnit, BigDecimal goldPrice,
                                        BigDecimal amount, boolean shipAsProcessing, boolean countCustomerGoldAsSales) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        if (goldPrice != null) {
            throw new BusinessException("不能按克数乘金价计费");
        }
        if (amount != null) {
            throw new BusinessException("金工收费时点尚未核定");
        }
        if (shipAsProcessing) {
            throw new BusinessException("金工交付不写普通发货");
        }
        if (countCustomerGoldAsSales) {
            throw new BusinessException("客户来料不记销售收入");
        }
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String payload = order.goldworkCode() + "|" + mark(qualityId) + "|" + plain(pieceCount) + "|" + plain(netWeight)
                + "|" + mark(weightUnit);
        String deliveryId = unit.execute(new CommandCall(
                requestId, actor.operator(), "DELIVER_GOLDWORK", "GOLDWORK_DELIVERY", order.goldworkCode(), payload
        ), session -> write(session, order, qualityId, pieceCount, netWeight, weightUnit));
        return view(deliveries.find(deliveryId).orElseThrow(() -> new BusinessException("交付不存在")));
    }

    private String write(WorkSession session, GoldworkOrder order, String qualityId, BigDecimal pieceCount,
                         BigDecimal netWeight, String weightUnit) {
        GoldworkQualityDecision quality = qualities.find(text(qualityId))
                .orElseThrow(() -> new BusinessException("放行份额不存在"));
        if (!quality.goldworkCode().equals(order.goldworkCode())) {
            throw new BusinessException("放行份额不属于这张金工单");
        }
        if (quality.outcome() != GoldworkQualityOutcome.RELEASE) {
            throw new BusinessException("只有放行份额可以交付");
        }
        boolean followed = qualities.list(order.goldworkCode()).stream()
                .anyMatch(item -> quality.qualityId().equals(item.priorQualityId()));
        if (followed) {
            throw new BusinessException("该份额已进入后续轮次，不能按原放行交付");
        }
        if (deliveries.existsForQuality(quality.qualityId(), session)) {
            throw new BusinessException("同一放行份额不能重复交付");
        }
        String deliveryId = "GD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        deliveries.insert(new GoldworkDelivery(
                deliveryId, order.goldworkCode(), quality.qualityId(), pieceCount, netWeight, weightUnit,
                quality.purity(), "UNCONFIRMED"
        ), session);
        return deliveryId;
    }

    private GoldworkDeliveryView view(GoldworkDelivery delivery) {
        return new GoldworkDeliveryView(
                delivery.deliveryId(), delivery.goldworkCode(), delivery.qualityId(), delivery.pieceCount(),
                delivery.netWeight(), delivery.weightUnit(), delivery.purity(), delivery.feeState(),
                false, false, false
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
