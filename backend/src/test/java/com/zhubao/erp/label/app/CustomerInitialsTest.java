package com.zhubao.erp.label.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerInitialsTest {

    @Test
    void 汉字取拼音首字母并处理常见多音词() {
        assertThat(CustomerInitials.of("恒信珠宝")).isEqualTo("HXZB");
        assertThat(CustomerInitials.of("重庆珠宝")).isEqualTo("CQZB");
        assertThat(CustomerInitials.of("银行")).isEqualTo("YH");
        assertThat(CustomerInitials.of("长春")).isEqualTo("CC");
        assertThat(CustomerInitials.of("厦门")).isEqualTo("XM");
        assertThat(CustomerInitials.of("Tom珠宝")).isEqualTo("TZB");
        assertThat(CustomerInitials.of("  ")).isEmpty();
    }
}
