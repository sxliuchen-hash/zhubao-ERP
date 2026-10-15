package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record ChildLineAllocation(String childOrderCode, String motherOrderCode, int sourceLineNo,
                                  String materialCode, BigDecimal quantity, BigDecimal weight, String ledgerCode) {
}
