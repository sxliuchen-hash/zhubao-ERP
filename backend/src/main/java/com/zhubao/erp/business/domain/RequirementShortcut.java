package com.zhubao.erp.business.domain;

/** 全厂共用的加工要求快捷词。写入母单后的要求文本不随快捷词删除而改变。 */
public record RequirementShortcut(long shortcutId, String phrase, int sortOrder) {
}
