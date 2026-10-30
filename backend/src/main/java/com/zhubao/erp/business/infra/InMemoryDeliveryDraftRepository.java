package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.delivery-draft.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryDeliveryDraftRepository implements DeliveryDraftRepository {

    private final CopyOnWriteArrayList<DeliveryDraft> headers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, List<DeliveryDraftLine>> lines = new ConcurrentHashMap<>();

    @Override
    public void save(DeliveryDraft draft, List<DeliveryDraftLine> draftLines, WorkSession session) {
        if (headers.stream().anyMatch(row -> row.deliveryId().equals(draft.deliveryId())
                || row.requestId().equals(draft.requestId()))) {
            throw new BusinessException("交付草稿已存在");
        }
        List<DeliveryDraftLine> stored = List.copyOf(draftLines);
        headers.add(draft);
        lines.put(draft.deliveryId(), stored);
        if (session != null) {
            session.onRollback(() -> {
                headers.remove(draft);
                lines.remove(draft.deliveryId(), stored);
            });
        }
    }

    @Override
    public Optional<DeliveryDraft> find(String deliveryId) {
        return headers.stream().filter(row -> row.deliveryId().equals(deliveryId)).findFirst();
    }

    @Override
    public Optional<DeliveryDraft> findByRequest(String requestId) {
        return headers.stream().filter(row -> row.requestId().equals(requestId)).findFirst();
    }

    @Override
    public List<DeliveryDraftLine> lines(String deliveryId) {
        return lines.getOrDefault(deliveryId, List.of()).stream()
                .sorted(Comparator.comparingInt(DeliveryDraftLine::lineNo))
                .toList();
    }

    @Override
    public List<String> listIds(WorkSession session) {
        return headers.stream().map(DeliveryDraft::deliveryId).sorted().toList();
    }
}
