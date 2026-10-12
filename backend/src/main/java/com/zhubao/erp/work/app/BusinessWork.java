package com.zhubao.erp.work.app;

import com.zhubao.erp.work.domain.WorkSession;

/** 在工作单元里执行的正式写入。返回需要回放的成功结果。 */
@FunctionalInterface
public interface BusinessWork {

    String run(WorkSession session);
}
