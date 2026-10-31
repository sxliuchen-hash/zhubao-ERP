package com.zhubao.erp.sampling.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.sampling.domain.SamplingDelivery;
import com.zhubao.erp.sampling.domain.SamplingDeliveryRepository;
import com.zhubao.erp.sampling.domain.SamplingFeeDecision;
import com.zhubao.erp.sampling.domain.SamplingFeeRepository;
import com.zhubao.erp.sampling.domain.SamplingFeeView;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
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
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * 按一次样品交付确认议定样费，或明确免收。
 * 免收不写零元应收。不写工资，也不把样费抵进普通大货。
 */
@Service
public class SamplingFeeService {

    private final SamplingOrderRepository orders;
    private final SamplingDeliveryRepository deliveries;
    private final SamplingFeeRepository fees;
    private final FinanceRepository finance;
    private final BusinessWorkUnit unit;
    private final PermissionGuard permissions;

    public SamplingFeeService(SamplingOrderRepository orders, SamplingDeliveryRepository deliveries,
                              SamplingFeeRepository fees, FinanceRepository finance, BusinessWorkUnit unit,
                              PermissionGuard permissions) {
        this.orders = orders;
        this.deliveries = deliveries;
        this.fees = fees;
        this.finance = finance;
        this.unit = unit;
        this.permissions = permissions;
    }

    public SamplingFeeView find(OperatorContext actor, String samplingCode, String deliveryId) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_VIEW, actor);
        SamplingOrder order = requireVisible(actor, samplingCode);
        SamplingDelivery delivery = requireDelivery(order.samplingCode(), deliveryId);
        return fees.findByDelivery(delivery.deliveryId()).map(this::view)
                .orElseThrow(() -> new BusinessException("这次交付的样费尚未确认"));
    }

    public SamplingFeeView settle(String requestId, OperatorContext actor, String samplingCode, String deliveryId,
                                  BigDecimal amount, boolean waive, String reason, String motherOrderCode,
                                  boolean settleWages) {
        if (settleWages) {
            throw new BusinessException("打样员工计酬尚未核定");
        }
        if (motherOrderCode != null && !motherOrderCode.isBlank()) {
            throw new BusinessException("样费不能抵大货");
        }
        if (waive && amount != null) {
            throw new BusinessException("不能同时确认样费和明确免收");
        }
        RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_MAINTAIN, actor);
        if (waive) {
            RefactorPermissionContract.assertAction(permissions, PermissionCode.SAMPLING_FEE_WAIVE, actor);
        } else {
            RefactorPermissionContract.assertAction(permissions, PermissionCode.FINANCE_BILLING_POST, actor);
            if (amount == null) {
                throw new BusinessException("请确认样费或明确免收");
            }
            if (amount.signum() == 0) {
                throw new BusinessException("未知金额不能写成 0");
            }
            if (amount.signum() < 0) {
                throw new BusinessException("样费必须大于零");
            }
            if (amount.scale() > 2) {
                throw new BusinessException("样费最多两位小数，不能截断后入账");
            }
        }
        SamplingOrder order = requireVisible(actor, samplingCode);
        SamplingDelivery delivery = requireDelivery(order.samplingCode(), deliveryId);
        String payload = delivery.deliveryId() + "|" + (waive ? "WAIVE" : "CHARGE") + "|"
                + (amount == null ? "" : amount.toPlainString()) + "|" + mark(reason);
        String feeId = unit.execute(new CommandCall(
                requestId, actor.operator(), "SETTLE_SAMPLING_FEE", "SAMPLING_FEE", delivery.deliveryId(), payload
        ), session -> write(session, actor, order, delivery, amount, waive, reason));
        return view(fees.findByDelivery(delivery.deliveryId()).filter(decision -> decision.feeId().equals(feeId))
                .orElseThrow(() -> new BusinessException("样费决定不存在")));
    }

    private String write(WorkSession session, OperatorContext actor, SamplingOrder order, SamplingDelivery delivery,
                         BigDecimal amount, boolean waive, String reason) {
        if (fees.lockByDelivery(delivery.deliveryId(), session).isPresent()) {
            throw new BusinessException("这次交付的样费已经确认");
        }
        String feeId = "SF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        if (waive) {
            fees.insert(new SamplingFeeDecision(
                    feeId, delivery.deliveryId(), order.samplingCode(), "WAIVE", null, reason, ""
            ), session);
            return feeId;
        }
        String receivableCode = "AR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        finance.saveReceivable(new ReceivableLine(
                receivableCode, order.customerId(), order.customerName(), delivery.deliveredOn().substring(0, 7),
                "SAMPLING_FEE", delivery.deliveryId(), "", order.samplingCode(), "样费",
                BigDecimal.ONE, ChargeUnit.JOB, amount, amount, "", "打样样费", actor.operator(), Instant.now().toString()
        ), session);
        fees.insert(new SamplingFeeDecision(
                feeId, delivery.deliveryId(), order.samplingCode(), "CHARGE", amount, "", receivableCode
        ), session);
        return feeId;
    }

    private SamplingFeeView view(SamplingFeeDecision decision) {
        return new SamplingFeeView(
                decision.feeId(), decision.deliveryId(), decision.samplingCode(), decision.decision(),
                decision.amount(), decision.reason(), decision.receivableCode(), "", false
        );
    }

    private SamplingOrder requireVisible(OperatorContext actor, String samplingCode) {
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE
                && (actor.customerScope() == null || actor.customerScope().isEmpty())) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
        SamplingOrder order = orders.find(samplingCode == null ? "" : samplingCode.trim())
                .orElseThrow(() -> new BusinessException("打样单不存在"));
        if (!CustomerScopeRules.allowed(actor, order.customerId(), order.customerName())) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
        return order;
    }

    private SamplingDelivery requireDelivery(String samplingCode, String deliveryId) {
        SamplingDelivery delivery = deliveries.find(deliveryId == null ? "" : deliveryId.trim())
                .orElseThrow(() -> new BusinessException("交付不存在"));
        if (!delivery.samplingCode().equals(samplingCode)) {
            throw new BusinessException("交付不属于这张打样单");
        }
        return delivery;
    }

    private static String mark(String value) {
        return value == null ? "" : value.trim().replace('|', '/');
    }
}
