package com.zhubao.erp.business.domain;

/**
 * 稳定工序身份。抛光（珠）和抛光（金）是两道不同工序。
 */
public record ProcessStep(String id, String name, BusinessKind kind) {
}
