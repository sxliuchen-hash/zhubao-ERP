package com.zhubao.erp.business.domain;

/**
 * 同一运单已经有母单。调用方应打开原单，不能再记一笔入库。
 */
public class DuplicateCourierException extends RuntimeException {

    public DuplicateCourierException() {
        super("快递单号已关联母单");
    }
}
