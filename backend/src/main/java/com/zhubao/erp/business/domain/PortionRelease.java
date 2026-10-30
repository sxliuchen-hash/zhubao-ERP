package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/** 一袋已经放行和冻结的数量。重复放行不把同一份再加一次。 */
public record PortionRelease(String bagId, BigDecimal released, BigDecimal frozen) {
}
