package com.zhubao.erp.business.domain;

import java.util.Optional;

/**
 * 只读取节点已到达份额。查询本身不通过本接口写入。
 */
public interface NodePortionReader {

    Optional<NodePortionBalance> find(String bagId, String stepId);
}
