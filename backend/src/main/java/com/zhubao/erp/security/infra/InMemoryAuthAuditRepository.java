package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditIntegrity;
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
        AuthAuditEvent previous = events.peekFirst();
        AuthAuditEvent sealed = AuthAuditIntegrity.seal(
                event,
                previous == null ? "" : previous.eventHash()
        );
        events.addFirst(sealed);
        while (events.size() > MAX_AUDIT_EVENTS) {
            events.removeLast();
        }
        return sealed;
    }

    /**
     * 撤回尚未提交完成的管理审计。只供同一包内的账号动作补偿使用。
     */
    void discard(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        events.removeIf(event -> eventId.equals(event.eventId()));
    }

    @Override
    public List<AuthAuditEvent> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_AUDIT_EVENTS));
        return events.stream().limit(safeLimit).toList();
    }
}
