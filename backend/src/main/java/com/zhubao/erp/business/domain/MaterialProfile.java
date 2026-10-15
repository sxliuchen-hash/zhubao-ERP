package com.zhubao.erp.business.domain;

public record MaterialProfile(String materialCode, String name, String aliases,
                              String remark, boolean enabled) {
}
