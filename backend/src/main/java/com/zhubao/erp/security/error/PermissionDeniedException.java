package com.zhubao.erp.security.error;

public class PermissionDeniedException extends RuntimeException {

    private final int code;

    public PermissionDeniedException(String message) {
        this(40301, message);
    }

    public PermissionDeniedException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
