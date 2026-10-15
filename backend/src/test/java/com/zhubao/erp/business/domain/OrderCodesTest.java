package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCodesTest {

    private final LocalDate day = LocalDate.of(2026, 10, 14);

    @Test
    void numbersNewOrderAsTypeDateAndFourDigits() {
        String code = OrderCodes.next("MD", day, List.of(), candidate -> false);

        assertThat(code).isEqualTo("MD-20261014-0001");
    }

    @Test
    void continuesAfterLegacyThreeDigitAndCurrentFourDigitSerials() {
        String code = OrderCodes.next("MD", day, List.of(
                "MD-20261014-008",
                "MD-20261014-0007",
                "MD-20261013-0999",
                "ZD-20261014-0500",
                "MD-KEEP-001"
        ), candidate -> false);

        assertThat(code).isEqualTo("MD-20261014-0009");
    }

    @Test
    void skipsCodesAlreadyTakenInThisBatch() {
        Set<String> reserved = Set.of("ZD-20261014-0001");

        String code = OrderCodes.next("ZD", day, List.of(), reserved::contains);

        assertThat(code).isEqualTo("ZD-20261014-0002");
    }

    @Test
    void stopsWhenTheDayIsExhausted() {
        assertThatThrownBy(() -> OrderCodes.next("MD", day, List.of("MD-20261014-9999"), candidate -> true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当天单号已用完");
    }
}
