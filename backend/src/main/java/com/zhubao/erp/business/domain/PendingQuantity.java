package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/** 某一袋在某一工序上仍待核的数量。待核不是损耗，也不能再拿去交工。 */
@FunctionalInterface
public interface PendingQuantity {

    BigDecimal pending(String bagId, String stepId);
}
