package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InitialBagAllocationTest {

    @Test
    void 多来源各建一袋且重复安排不追加() {
        InitialBagAllocationService service = new InitialBagAllocationService(
                new ActivityBagService(new InMemoryActivityBagRepository()));
        List<InitialBagAllocationService.SourceLine> lines = List.of(
                line("LINE-1", "10"),
                line("LINE-2", "6"));
        List<ActivityBag> first = service.arrange("ZD-20261030-0008", lines, null);
        List<ActivityBag> second = service.arrange("ZD-20261030-0008", lines, null);

        assertThat(first).hasSize(2);
        assertThat(first).extracting(ActivityBag::sourceKey).containsExactly("LINE-1", "LINE-2");
        assertThat(first).extracting(ActivityBag::id).containsExactlyElementsOf(second.stream().map(ActivityBag::id).toList());
        assertThat(first).allMatch(bag -> !bag.closed());
    }

    private static InitialBagAllocationService.SourceLine line(String key, String quantity) {
        return new InitialBagAllocationService.SourceLine(
                key, "AU9999", "REQ-1", new BigDecimal(quantity), StockMeasurement.unknown());
    }
}
