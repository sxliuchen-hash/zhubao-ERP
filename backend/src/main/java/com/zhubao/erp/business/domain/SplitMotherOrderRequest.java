package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 拆分子单的请求体。母单号在路径上，操作人取当前登录身份。
 */
public record SplitMotherOrderRequest(
        String requestId,
        List<SplitMotherOrderLine> lines
) {
    public SplitMotherOrderRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
