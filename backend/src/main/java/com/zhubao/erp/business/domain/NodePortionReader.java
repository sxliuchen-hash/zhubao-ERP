package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

/**
 * 只读取节点已到达份额。可办理查询不写入。
 * 带会话的读取供报工命令在同一工作单元里重读限额。
 */
public interface NodePortionReader {

    Optional<NodePortionBalance> find(String bagId, String stepId);

    default Optional<NodePortionBalance> find(String bagId, String stepId, WorkSession session) {
        return find(bagId, stepId);
    }
}
