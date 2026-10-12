package com.zhubao.erp.work.app;

import com.zhubao.erp.work.domain.CommandCall;

/**
 * 有限的命令回放与提交边界。
 * 同账号、同请求、同内容回放原结果；内容不一致或换成其他账号都拒绝。
 * 来料建单和入库确认若改用 {@link com.zhubao.erp.work.domain.WorkSession} 写入，会进入本单元。
 * 报工、质检、发货和登记导入仍调用各自提交的仓储方法，不在本单元里。
 * 外层 {@code @Transactional} 不能把那些独立提交收进同一次事务。
 */
public interface BusinessWorkUnit {

    String execute(CommandCall call, BusinessWork work);
}
