package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BagSplitServiceTest {

    @Test
    void 分袋数量守恒且原请求不再次分袋() {
        InMemoryActivityBagRepository repository = new InMemoryActivityBagRepository();
        ActivityBagService bags = new ActivityBagService(repository);
        ActivityBag parent = bags.open(
                "ZD-20261030-0009", "AU9999", "REQ-1", "LINE-1",
                new BigDecimal("10"), StockMeasurement.unknown(), null);
        BagSplitService service = new BagSplitService(
                repository, bags, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));

        String first = service.split("split-001", "wh", parent.id(), List.of(new BigDecimal("4"), new BigDecimal("6")));
        String replay = service.split("split-001", "wh", parent.id(), List.of(new BigDecimal("4"), new BigDecimal("6")));

        assertThat(replay).isEqualTo(first);
        assertThat(repository.find(parent.id()).orElseThrow().closed()).isTrue();
        assertThat(bags.activeLeaves(parent.targetCode())).hasSize(2);
        assertThat(bags.activeLeaves(parent.targetCode()).stream().map(ActivityBag::quantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10");
        assertThatThrownBy(() -> service.split("split-002", "wh", parent.id(), List.of(new BigDecimal("10"))))
                .isInstanceOf(BusinessException.class);
    }
}
