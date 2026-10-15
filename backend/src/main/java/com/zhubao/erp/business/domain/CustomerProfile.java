package com.zhubao.erp.business.domain;

public record CustomerProfile(long customerId, String customerCode, String name,
                              String aliases, String contact, String phone, String remark, boolean enabled) {
}
