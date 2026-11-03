package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingDelivery;
import com.zhubao.erp.sampling.domain.SamplingFeeDecision;
import com.zhubao.erp.sampling.domain.SamplingMakingChoice;
import com.zhubao.erp.sampling.domain.SamplingOperationReport;
import com.zhubao.erp.sampling.domain.SamplingOrderStatus;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.sampling.infra.InMemorySamplingDeliveryRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingFeeRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOperationRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SamplingCompletionTest {

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);

    @Test
    void 已报数量交完且样费确认或免收后可以完结() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        InMemorySamplingOperationRepository operations = new InMemorySamplingOperationRepository();
        InMemorySamplingDeliveryRepository deliveries = new InMemorySamplingDeliveryRepository();
        InMemorySamplingFeeRepository fees = new InMemorySamplingFeeRepository();
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        var order = new SamplingOrderService(customers, orders, unit, new PermissionGuard()).open(
                "s07-order-01", keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
        SamplingCompletionService service = new SamplingCompletionService(
                orders, operations, deliveries, fees, unit, new PermissionGuard());
        assertThatThrownBy(() -> service.complete("s07-early-1", keeper, order.samplingCode()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("还没有选料与加工的实物报工，不能完结");

        unit.execute(new CommandCall("s07-seed-rp", keeper.operator(), "SEED_REPORT", "SAMPLING_OPERATION",
                order.samplingCode(), "report"), session -> {
            operations.insert(new SamplingOperationReport(
                    "SR-S07", order.samplingCode(), "SAMPLE_MAKE", SamplingVersionKind.MODEL, 1,
                    SamplingMakingChoice.CARVE_AND_SHAPE, new BigDecimal("2"), "颗", "ATT-1", "张三", "2026-11-03"
            ), session);
            return "SR-S07";
        });
        unit.execute(new CommandCall("s07-seed-dv", keeper.operator(), "SEED_DELIVERY", "SAMPLING_DELIVERY",
                order.samplingCode(), "SD-S07"), session -> {
            deliveries.insert(new SamplingDelivery(
                    "SD-S07", order.samplingCode(), SamplingVersionKind.MODEL, 1, "", new BigDecimal("2"), "颗", "ATT-1", "2026-11-03"
            ), session);
            return "SD-S07";
        });
        assertThatThrownBy(() -> service.complete("s07-nofee1", keeper, order.samplingCode()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("还有样品交付没有确认样费或明确免收");
        unit.execute(new CommandCall("s07-seed-fe", keeper.operator(), "SEED_FEE", "SAMPLING_FEE",
                order.samplingCode(), "fee"), session -> {
            fees.insert(new SamplingFeeDecision(
                    "SF-S07", "SD-S07", order.samplingCode(), "WAIVE", null, "样品确认免收", ""
            ), session);
            return "SF-S07";
        });

        var completed = service.complete("s07-done-01", keeper, order.samplingCode());
        var replay = service.complete("s07-done-01", keeper, order.samplingCode());
        assertThat(replay.version()).isEqualTo(completed.version());
        assertThat(completed.status()).isEqualTo(SamplingOrderStatus.COMPLETED.name());
        assertThat(orders.find(order.samplingCode()).orElseThrow().status()).isEqualTo(SamplingOrderStatus.COMPLETED);
        assertThat(service.complete("s07-again1", keeper, order.samplingCode()).status())
                .isEqualTo(SamplingOrderStatus.COMPLETED.name());
    }
}
