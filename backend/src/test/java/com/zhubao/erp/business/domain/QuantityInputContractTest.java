package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityInputContractTest {

    @Test
    void 未传来源按手填且空识别不能当成可用计数() {
        QuantityInput manual = new QuantityInput(null, new BigDecimal("12"), null, null);
        assertThat(manual.source()).isEqualTo(QuantityInput.Source.MANUAL);
        assertThat(manual.confirmedQuantity()).isEqualByComparingTo("12");
        assertThatThrownBy(() -> new QuantityInput(QuantityInput.Source.PHOTO_COUNT, new BigDecimal("12"), new BigDecimal("11"), ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未接入");
        QuantityInput reserved = new QuantityInput(
                QuantityInput.Source.PHOTO_COUNT, new BigDecimal("12"), new BigDecimal("11"), "COUNT-REF-1");
        assertThat(reserved.recognizedQuantity()).isEqualByComparingTo("11");
        assertThat(reserved.confirmedQuantity()).isEqualByComparingTo("12");
    }
}
