package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCodeReferenceTest {

    @Test
    void 单袋直达多袋选择且关闭父袋只读() {
        InMemoryActivityBagRepository repository = new InMemoryActivityBagRepository();
        ActivityBagService service = new ActivityBagService(repository);
        ActivityBag only = service.open("ZD-20261030-0004", "AU9999", "REQ-1", "LINE-1",
                new BigDecimal("3"), StockMeasurement.unknown(), null);
        BagCodeReader.Decision direct = BagCodeReader.read(repository, "JG|work_order|ZD-20261030-0004");
        assertThat(direct.directBagId()).isEqualTo(only.id());
        assertThat(direct.choices()).isEmpty();

        ActivityBag parent = service.open("ZD-20261030-0005", "AU9999", "REQ-1", "LINE-1",
                new BigDecimal("8"), StockMeasurement.unknown(), null);
        repository.save(new ActivityBag("BAG-A", parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), parent.id(), false, new BigDecimal("3"), StockMeasurement.unknown(), 1), 0, null);
        repository.save(new ActivityBag("BAG-B", parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), parent.id(), false, new BigDecimal("5"), StockMeasurement.unknown(), 1), 0, null);
        service.close(parent.id(), null);
        BagCodeReader.Decision choice = BagCodeReader.read(repository, parent.id());
        assertThat(choice.closedParent()).isTrue();
        assertThat(choice.directBagId()).isNull();
        assertThat(choice.choices()).containsExactly("BAG-A", "BAG-B");
        BagCodeReader.Decision active = BagCodeReader.read(repository, "BAG-A");
        assertThat(active.directBagId()).isEqualTo("BAG-A");
        assertThat(active.choices()).isEmpty();
        assertThat(active.closedParent()).isFalse();
        BagCodeReader.Decision target = BagCodeReader.read(repository, parent.targetCode());
        assertThat(target.directBagId()).isNull();
        assertThat(target.choices()).containsExactly("BAG-A", "BAG-B");
        assertThat(BagCodeReader.reference("DY-20261030-0001").kind()).isEqualTo(BusinessKind.SAMPLING);
        assertThat(BagCodeReader.reference("GJ-20261030-0001").kind()).isEqualTo(BusinessKind.GOLDWORK);
        assertThat(BagCodeReader.reference("MD-20261030-0001").processingMother()).isTrue();
    }
}
