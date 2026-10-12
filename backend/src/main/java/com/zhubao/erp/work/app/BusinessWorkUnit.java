package com.zhubao.erp.work.app;

import com.zhubao.erp.work.domain.CommandCall;

/**
 * 有限的命令回放与提交边界。
 * 同账号、同请求、同内容回放原结果；内容不一致或换成其他账号都拒绝。
 * 现有库存仓储和业务单据仓储的 JDBC 写入仍各自取连接并提交，尚未进入本单元。
 * 外层 {@code @Transactional} 不能把那些独立提交收进同一次事务。
 */
public interface BusinessWorkUnit {

    String execute(CommandCall call, BusinessWork work);
}
