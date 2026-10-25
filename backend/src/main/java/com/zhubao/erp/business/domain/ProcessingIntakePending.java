package com.zhubao.erp.business.domain;

/** 待核资料以账号隔离；转正式单与状态更新必须同事务。 */
public record ProcessingIntakePending(String code, int version, String account, String status,
                                      String motherOrderCode, String draftJson, String createdAt, String updatedAt) { }
