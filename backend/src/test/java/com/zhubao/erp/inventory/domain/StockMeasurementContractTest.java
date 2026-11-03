package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMeasurementContractTest {

    @Test
    void 未知重量不是零且单位不能混加() {
        StockMeasurement.Weight unknown = StockMeasurement.unknown();
        assertThat(unknown.state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(unknown.value()).isNull();
        assertThat(StockMeasurement.known(null).state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        StockMeasurement.assertSameUnit("颗", "颗");
        assertThatThrownBy(() -> StockMeasurement.assertSameUnit("颗", "件"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 外协不改变权属且金工精度不截断() {
        assertThat(StockMeasurement.ownerAfterOutsourcing(OwnerType.CUSTOMER)).isEqualTo(OwnerType.CUSTOMER);
        assertThat(StockMeasurement.ownerAfterOutsourcing(OwnerType.COMPANY)).isEqualTo(OwnerType.COMPANY);
        assertThatThrownBy(() -> StockMeasurement.processingScale(new BigDecimal("1.2345")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能截断");
        assertThat(StockMeasurement.processingScale(new BigDecimal("1.200"))).isEqualByComparingTo("1.200");
        assertThatThrownBy(() -> StockMeasurement.rejectUndecidedGoldScale(BusinessKind.GOLDWORK))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只作记录");
        StockMeasurement.rejectUndecidedGoldScale(BusinessKind.PROCESSING);
    }
}
