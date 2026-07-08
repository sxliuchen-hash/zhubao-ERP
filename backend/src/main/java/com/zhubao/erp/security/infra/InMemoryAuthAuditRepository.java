package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.audit.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthAuditRepository implements AuthAuditRepository {

    private static final int MAX_AUDIT_EVENTS = 50;

    private final Deque<AuthAuditEvent> events = new ConcurrentLinkedDeque<>();

    @Override
    public AuthAuditEvent save(AuthAuditEvent event) {
        events.addFirst(event);
        while (events.size() > MAX_AUDIT_EVENTS) {
            events.removeLast();
        }
        return event;
    }

    @Override
    public List<AuthAuditEvent> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_AUDIT_EVENTS));
        return events.stream().limit(safeLimit).toList();
    }
}
