package com.zhubao.erp.security.error;

/**
 * 登录会话不存在、已退出、已过期、已吊销，或账号密码与权限已变更。
 * HTTP 仍是 403，业务码仍是 40301，但文案固定，前端只据此清理令牌。
 * 普通业务无权限继续使用各自的原因，不能套用这句文案。
 */
public class SessionInvalidException extends PermissionDeniedException {

    public static final String CLIENT_MESSAGE = "登录已失效，请重新登录";

    public SessionInvalidException() {
        super(40301, CLIENT_MESSAGE);
    }
}
