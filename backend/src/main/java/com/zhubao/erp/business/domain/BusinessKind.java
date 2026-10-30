package com.zhubao.erp.business.domain;

/**
 * 三类业务的稳定种类。普通加工沿用现有母单和子单，打样与金工使用各自单号。
 */
public enum BusinessKind {
    PROCESSING,
    SAMPLING,
    GOLDWORK
}
