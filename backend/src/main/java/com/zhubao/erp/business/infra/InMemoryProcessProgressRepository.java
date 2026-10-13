package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ProcessProgressEvent;
import com.zhubao.erp.business.domain.ProcessProgressHead;
import com.zhubao.erp.business.domain.ProcessProgressRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryProcessProgressRepository implements ProcessProgressRepository {

    private final ConcurrentHashMap<String, ProcessProgressHead> heads = new ConcurrentHashMap<>();
    private final List<ProcessProgressEvent> events = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<ProcessProgressHead> find(String orderCode, String processName) {
        return Optional.ofNullable(heads.get(key(orderCode, processName)));
    }

    @Override
    public Optional<ProcessProgressHead> find(String orderCode, String processName, WorkSession session) {
        requireSession(session);
        return find(orderCode, processName);
    }

    @Override
    public List<ProcessProgressHead> listAll() {
        return List.copyOf(heads.values());
    }

    @Override
    public synchronized void save(ProcessProgressHead head, WorkSession session) {
        requireSession(session);
        String key = key(head.orderCode(), head.processName());
        ProcessProgressHead previous = heads.put(key, head);
        session.onRollback(() -> {
            synchronized (this) {
                if (previous == null) {
                    heads.remove(key, head);
                } else {
                    heads.put(key, previous);
                }
            }
        });
    }

    @Override
    public synchronized ProcessProgressEvent append(ProcessProgressEvent event, WorkSession session) {
        requireSession(session);
        ProcessProgressEvent saved = new ProcessProgressEvent(
                sequence.incrementAndGet(),
                event.orderCode(),
                event.processName(),
                event.oldPercent(),
                event.newPercent(),
                event.note(),
                event.reason(),
                event.operator(),
                event.createdAt()
        );
        events.add(saved);
        long id = saved.id();
        session.onRollback(() -> {
            synchronized (this) {
                events.removeIf(row -> row.id() == id);
            }
        });
        return saved;
    }

    @Override
    public List<ProcessProgressEvent> history(String orderCode, String processName) {
        return events.stream()
                .filter(event -> orderCode.equals(event.orderCode()) && processName.equals(event.processName()))
                .toList();
    }

    private static String key(String orderCode, String processName) {
        return orderCode + "|" + processName;
    }

    private static void requireSession(WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("进度写入必须处于业务工作单元");
        }
    }
}
