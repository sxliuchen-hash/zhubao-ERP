package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/**
 * 某一袋在某一工序上已经到达、已经处理的数量。
 * 已处理含交下道和退出本路线，不含待核。未知重量不在这里用 0 代替。
 */
public record NodePortionBalance(String bagId, String stepId, BigDecimal arrived, BigDecimal handled) {

    public NodePortionBalance {
        bagId = required(bagId, "袋");
        stepId = required(stepId, "工序");
        if (arrived == null || arrived.signum() < 0 || handled == null || handled.signum() < 0) {
            throw new BusinessException("节点份额不能为负");
        }
        if (handled.compareTo(arrived) > 0) {
            throw new BusinessException("已处理数量不能超过已到达数量");
        }
    }

    public BigDecimal remaining() {
        return arrived.subtract(handled);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("节点份额缺少" + label);
        }
        return value.trim();
    }
}
