package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record SplitSourceAllocation(int sourceLineNo, BigDecimal quantity, BigDecimal weight) {
}
