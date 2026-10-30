package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

/**
 * 保存节点份额。必须和报工批次处在同一次工作单元里。
 */
public interface NodePortionWriter {

    void save(NodePortionBalance balance, WorkSession session);
}
