package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessTargetReferenceTest {

    @Test
    void 母单子单打样和金工号可以区分并来回解析() {
        BusinessTargetReference mother = BusinessTargetReference.parse("MD-20261030-0001");
        BusinessTargetReference child = BusinessTargetReference.parse("ZD-20261030-0007");
        BusinessTargetReference sampling = BusinessTargetReference.parse("DY-20261030-0001");
        BusinessTargetReference goldwork = BusinessTargetReference.parse("GJ-20261030-0012");

        assertThat(mother.processingMother()).isTrue();
        assertThat(child.processingMother()).isFalse();
        assertThat(child.kind()).isEqualTo(BusinessKind.PROCESSING);
        assertThat(sampling.kind()).isEqualTo(BusinessKind.SAMPLING);
        assertThat(sampling.processingMother()).isFalse();
        assertThat(goldwork.kind()).isEqualTo(BusinessKind.GOLDWORK);
        assertThat(goldwork.processingMother()).isFalse();
        assertThat(BusinessTargetReference.parse(mother.canonical())).isEqualTo(mother);
        assertThat(BusinessTargetReference.parse(sampling.canonical())).isEqualTo(sampling);
    }

    @Test
    void 空号未知号和种类冲突被拒绝() {
        assertThatThrownBy(() -> BusinessTargetReference.parse(" ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> BusinessTargetReference.parse(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> BusinessTargetReference.parse("MO20260709002")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> BusinessTargetReference.parse("XX-20261030-0001")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> BusinessTargetReference.parse("PROCESSING|MOTHER|DY-20261030-0001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void 新单号沿用日期流水且不占用母单前缀() {
        LocalDate day = LocalDate.of(2026, 10, 30);
        String sampling = BusinessTargetReference.nextSamplingCode(day, List.of("DY-20261030-0003"), ignored -> false);
        String goldwork = BusinessTargetReference.nextGoldworkCode(day, List.of(), code -> "GJ-20261030-0001".equals(code));

        assertThat(sampling).isEqualTo("DY-20261030-0004");
        assertThat(goldwork).isEqualTo("GJ-20261030-0002");
        assertThat(BusinessTargetReference.fromCode(sampling).processingMother()).isFalse();
        assertThat(BusinessTargetReference.fromCode(goldwork).kind()).isEqualTo(BusinessKind.GOLDWORK);
    }
}
