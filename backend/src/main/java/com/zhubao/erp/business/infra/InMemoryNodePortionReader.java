package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点份额的内存读取。尚未提供 JDBC 实现，MySQL 档案不能把本类当成已落库。
 * 保存只供后续报工命令和测试安排份额；可办理查询不会调用它。
 */
public class InMemoryNodePortionReader implements NodePortionReader {

    private final ConcurrentHashMap<String, NodePortionBalance> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<NodePortionBalance> find(String bagId, String stepId) {
        if (bagId == null || stepId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(key(bagId.trim(), stepId.trim())));
    }

    public void save(NodePortionBalance balance, WorkSession session) {
        String key = key(balance.bagId(), balance.stepId());
        NodePortionBalance previous = rows.get(key);
        rows.put(key, balance);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    rows.remove(key, balance);
                } else {
                    rows.put(key, previous);
                }
            });
        }
    }

    public int size() {
        return rows.size();
    }

    private static String key(String bagId, String stepId) {
        return bagId + "\n" + stepId;
    }
}
