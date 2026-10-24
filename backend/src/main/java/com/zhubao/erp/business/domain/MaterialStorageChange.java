package com.zhubao.erp.business.domain;

/** 材质保存要求变更记录，业务快照不会随之覆盖。 */
public record MaterialStorageChange(String materialCode, StorageRequirements previous, StorageRequirements current, String changedAt) { }
