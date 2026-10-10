package com.zhubao.erp.security.domain;

/**
 * 停用账号之后单独重试会话吊销的结果。不重新执行账号申请。
 */
public record SessionRevocationResult(String username, int revokedSessions) {
}
