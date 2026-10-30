package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.ConsumableCostEvent;
import com.zhubao.erp.inventory.domain.ConsumableCostRepository;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.consumable-cost.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryConsumableCostRepository implements ConsumableCostRepository {

    private final ConcurrentHashMap<String, Rule> rules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsumableCostEvent> events = new ConcurrentHashMap<>();

    @Override
    public void saveRule(String issueId, ConsumptionTiming timing, boolean costPosted, String receiptId, WorkSession session) {
        Rule previous = rules.put(issueId, new Rule(timing, costPosted, receiptId));
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    rules.remove(issueId);
                } else {
                    rules.put(issueId, previous);
                }
            });
        }
    }

    @Override
    public ConsumptionTiming timingOf(String issueId) {
        Rule rule = rules.get(issueId);
        return rule == null ? null : rule.timing;
    }

    @Override
    public String receiptIdOf(String issueId) {
        Rule rule = rules.get(issueId);
        return rule == null ? "" : rule.receiptId;
    }

    @Override
    public boolean costPosted(String issueId) {
        Rule rule = rules.get(issueId);
        return rule != null && rule.costPosted;
    }

    @Override
    public void markCostPosted(String issueId, WorkSession session) {
        Rule current = rules.get(issueId);
        if (current == null) {
            throw new BusinessException("领用计耗规则不存在");
        }
        rules.put(issueId, new Rule(current.timing, true, current.receiptId));
        if (session != null) {
            session.onRollback(() -> rules.put(issueId, current));
        }
    }

    @Override
    public void insert(ConsumableCostEvent event, WorkSession session) {
        if (events.putIfAbsent(event.eventId(), event) != null) {
            throw new BusinessException("耗材费用已存在");
        }
        if (session != null) {
            session.onRollback(() -> events.remove(event.eventId(), event));
        }
    }

    @Override
    public List<ConsumableCostEvent> events(String issueId) {
        return events.values().stream().filter(event -> event.issueId().equals(issueId)).toList();
    }

    public Optional<ConsumableCostEvent> find(String eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    private record Rule(ConsumptionTiming timing, boolean costPosted, String receiptId) {
    }
}
