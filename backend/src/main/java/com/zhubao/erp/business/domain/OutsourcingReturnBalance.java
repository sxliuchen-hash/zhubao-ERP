package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/**
 * 一张发外的收回核对。少回时仍在外，待核不等于已确认损耗，也不表示已经结清。
 */
public record OutsourcingReturnBalance(
        BigDecimal dispatched,
        BigDecimal received,
        BigDecimal unprocessed,
        BigDecimal confirmedLoss,
        BigDecimal stillOut,
        boolean pending,
        boolean settled
) {
}
