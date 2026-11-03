package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMovementKind;
import com.zhubao.erp.goldwork.domain.GoldworkMovementRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkQualityOutcome;
import com.zhubao.erp.goldwork.domain.GoldworkQualityRepository;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.app.RefactorPermissionContract;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

/**
 * 金工结清资格。计酬和成本归属未核定，待核或未回收时不能把单据改成完成。
 */
@Service
public class GoldworkCompletionService {

    private final GoldworkOrderRepository orders;
    private final GoldworkMovementRepository movements;
    private final GoldworkRecoveryRepository recoveries;
    private final GoldworkQualityRepository qualities;
    private final PermissionGuard permissions;

    public GoldworkCompletionService(GoldworkOrderRepository orders, GoldworkMovementRepository movements,
                                     GoldworkRecoveryRepository recoveries, GoldworkQualityRepository qualities,
                                     PermissionGuard permissions) {
        this.orders = orders;
        this.movements = movements;
        this.recoveries = recoveries;
        this.qualities = qualities;
        this.permissions = permissions;
    }

    public void complete(OperatorContext actor, String goldworkCode, boolean countOutsourcingAsOutput) {
        RefactorPermissionContract.assertAction(permissions, PermissionCode.GOLDWORK_MAINTAIN, actor);
        GoldworkOrder order = requireVisible(actor, goldworkCode);
        String code = order.goldworkCode();
        boolean pending = qualities.list(code).stream().anyMatch(item -> item.outcome() == GoldworkQualityOutcome.PENDING)
                || movements.list(code).stream().anyMatch(item -> "HOLD".equals(item.disposition()));
        if (pending) {
            throw new BusinessException("待核不能悄悄结清");
        }
        if (recoveries.list(code).stream().anyMatch(item -> item.netWeight() == null)) {
            throw new BusinessException("未称重的回收不能悄悄结清");
        }
        boolean hasOutput = movements.list(code).stream().anyMatch(item -> item.kind() == GoldworkMovementKind.OUTPUT);
        if (hasOutput && recoveries.list(code).isEmpty()) {
            throw new BusinessException("未回收不能悄悄结清");
        }
        if (countOutsourcingAsOutput) {
            throw new BusinessException("外协不算员工产量");
        }
        throw new BusinessException("金工本版只记录件数，不核算工资");
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
}
