package com.zhubao.erp.goldwork.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMovement;
import com.zhubao.erp.goldwork.domain.GoldworkMovementKind;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
import com.zhubao.erp.goldwork.domain.GoldworkOrderView;
import com.zhubao.erp.goldwork.domain.GoldworkQualityDecision;
import com.zhubao.erp.goldwork.domain.GoldworkQualityOutcome;
import com.zhubao.erp.goldwork.domain.GoldworkRecovery;
import com.zhubao.erp.goldwork.domain.GoldworkRecoveryKind;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkMovementRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkOrderRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkQualityRepository;
import com.zhubao.erp.goldwork.infra.InMemoryGoldworkRecoveryRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldworkCompletionSettlementTest {

    private final OperatorContext keeper = new OperatorContext("金工", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 未回收待核和外协都不能把金工单结清() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryGoldworkOrderRepository orders = new InMemoryGoldworkOrderRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        GoldworkOrderView order = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m10-order-01", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "戒托", "张三", "", "", "");
        InMemoryGoldworkMovementRepository movements = new InMemoryGoldworkMovementRepository();
        InMemoryGoldworkRecoveryRepository recoveries = new InMemoryGoldworkRecoveryRepository();
        InMemoryGoldworkQualityRepository qualities = new InMemoryGoldworkQualityRepository();
        GoldworkCompletionService service = new GoldworkCompletionService(orders, movements, recoveries, qualities, guard);
        OperatorContext finance = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL);
        assertThatThrownBy(() -> service.complete(finance, order.goldworkCode(), false))
                .isInstanceOf(PermissionDeniedException.class);

        assertThatThrownBy(() -> service.complete(keeper, order.goldworkCode(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工计酬尚未核定，不能结清");

        movements.insert(movement(order.goldworkCode(), "GM-OUT", GoldworkMovementKind.OUTPUT, "", new BigDecimal("1.20000")), null);
        assertThatThrownBy(() -> service.complete(keeper, order.goldworkCode(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未回收不能悄悄结清");

        recoveries.insert(recovery(order.goldworkCode(), "GR-OPEN", null), null);
        assertThatThrownBy(() -> service.complete(keeper, order.goldworkCode(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未称重的回收不能悄悄结清");

        recoveries.insert(recovery(order.goldworkCode(), "GR-WEIGH", new BigDecimal("0.20000")), null);
        qualities.insert(new GoldworkQualityDecision(
                "GQ-PEND", order.goldworkCode(), "GO-1", "", 1, GoldworkQualityOutcome.PENDING,
                null, new BigDecimal("0.1"), "克", "足金", "边角待核"
        ), null);
        assertThatThrownBy(() -> service.complete(keeper, order.goldworkCode(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("待核不能悄悄结清");

        GoldworkOrderView clear = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m10-order-02", keeper, customer.customerId(), "COMPANY_PREPARED", "吊坠", "李四", "", "", "");
        movements.insert(movement(clear.goldworkCode(), "GM-OUT2", GoldworkMovementKind.OUTPUT, "", new BigDecimal("0.80000")), null);
        movements.insert(movement(clear.goldworkCode(), "GM-HOLD", GoldworkMovementKind.DISPOSAL, "HOLD", null), null);
        recoveries.insert(recovery(clear.goldworkCode(), "GR-CLEAR", new BigDecimal("0.10000")), null);
        assertThatThrownBy(() -> service.complete(keeper, clear.goldworkCode(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("待核不能悄悄结清");

        GoldworkOrderView ready = new GoldworkOrderService(customers, orders, unit, guard).open(
                "m10-order-03", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "耳钉", "王五", "", "", "");
        movements.insert(movement(ready.goldworkCode(), "GM-OUT3", GoldworkMovementKind.OUTPUT, "", new BigDecimal("0.50000")), null);
        recoveries.insert(recovery(ready.goldworkCode(), "GR-READY", new BigDecimal("0.05000")), null);
        assertThatThrownBy(() -> service.complete(keeper, ready.goldworkCode(), true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协不算员工产量");
        assertThatThrownBy(() -> service.complete(keeper, ready.goldworkCode(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("金工计酬尚未核定，不能结清");
        assertThat(orders.find(order.goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
        assertThat(orders.find(clear.goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
        assertThat(orders.find(ready.goldworkCode()).orElseThrow().status()).isEqualTo(GoldworkOrderStatus.OPEN);
    }

    private static GoldworkMovement movement(String goldworkCode, String movementId, GoldworkMovementKind kind,
                                             String disposition, BigDecimal net) {
        return new GoldworkMovement(
                movementId, goldworkCode, "GOLD_CAST", kind, "GL-1", OwnerType.CUSTOMER,
                null, net, null, net == null ? "" : "克", "足金", disposition, "", "UNDECIDED"
        );
    }

    private static GoldworkRecovery recovery(String goldworkCode, String recoveryId, BigDecimal net) {
        return new GoldworkRecovery(
                recoveryId, goldworkCode, "GL-1", "GM-OUT", GoldworkRecoveryKind.RECOVERABLE, OwnerType.CUSTOMER,
                null, net, null, net == null ? "" : "克", "足金", "UNDECIDED"
        );
    }
}
