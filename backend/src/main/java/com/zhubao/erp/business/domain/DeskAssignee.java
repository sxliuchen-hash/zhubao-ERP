package com.zhubao.erp.business.domain;

/** 可指派的启用账号。只含用户名和姓名，不含权限、范围或口令。 */
public record DeskAssignee(String username, String operator) {
}
