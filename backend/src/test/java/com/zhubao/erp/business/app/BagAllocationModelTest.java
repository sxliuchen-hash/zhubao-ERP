package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BagAllocationModelTest {

    @Test
    void 关闭父袋后不再计入可办理量且未知重量不是零() {
        InMemoryActivityBagRepository repository = new InMemoryActivityBagRepository();
        ActivityBagService service = new ActivityBagService(repository);
        ActivityBag parent = service.open(
                "ZD-20261030-0001", "AU9999", "REQ-1", "LINE-1",
                new BigDecimal("10"), StockMeasurement.unknown(), null);
        repository.save(new ActivityBag(
                "BAG-LEAF", parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), parent.id(), false, new BigDecimal("4"),
                StockMeasurement.known(new BigDecimal("1.200")), 1), 0, null);
        service.close(parent.id(), null);

        assertThat(parent.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(parent.weight().value()).isNull();
        assertThat(service.activeLeaves("ZD-20261030-0001")).extracting(ActivityBag::id).containsExactly("BAG-LEAF");
        assertThat(repository.find(parent.id()).orElseThrow().activeLeaf()).isFalse();
        assertThatThrownBy(() -> service.open(
                "ZD-20261030-0001", "", "REQ-1", "LINE-1", BigDecimal.ONE, StockMeasurement.unknown(), null))
                .isInstanceOf(BusinessException.class);
    }
}
