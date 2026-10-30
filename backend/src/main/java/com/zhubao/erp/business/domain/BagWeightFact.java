package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record BagWeightFact(String bagId, BagWeightState state, BigDecimal value, int version) {
}
