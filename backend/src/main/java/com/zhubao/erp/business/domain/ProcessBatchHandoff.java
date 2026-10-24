package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/** 分批交工事实；数量为零时只登记末道总重量，不重复交出数量。 */
public record ProcessBatchHandoff(String batchCode, String orderCode, String processName,
                                  BigDecimal quantity, BigDecimal finalWeight, String operator, String createdAt) {
}
