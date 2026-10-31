package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.NodePortionWriter;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点份额的内存实现。MySQL 档案改走 JDBC，不能在目标库上继续使用本类。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.whole-bag-report.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryNodePortionReader implements NodePortionReader, NodePortionWriter {

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

    @Override
    public java.util.List<NodePortionBalance> list(String bagId) {
        return rows.values().stream().filter(row -> row.bagId().equals(bagId)).toList();
    }

    public int size() {
        return rows.size();
    }

    private static String key(String bagId, String stepId) {
        return bagId + "\n" + stepId;
    }
}
