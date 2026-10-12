package com.zhubao.erp.work.domain;

/** 同一请求编号已经占用，调用方应回读并决定回放或拒绝。 */
public class DuplicateCommandRequestException extends RuntimeException {

    public DuplicateCommandRequestException(Throwable cause) {
        super("命令请求已存在", cause);
    }
}
