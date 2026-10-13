package com.zhubao.erp.business.domain;

/**
 * 选择不拆的请求体。母单号在路径上，操作人取当前登录身份。
 */
public record KeepMotherOrderRequest(
        String requestId
) {
}
