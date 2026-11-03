package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.DeliveryConfirmedSource;
import com.zhubao.erp.business.domain.DeliveryDraft;
import com.zhubao.erp.business.domain.DeliveryDraftLine;
import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.business.domain.DeliveryDraftStatus;
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
    private final ConcurrentHashMap<String, List<DeliveryConfirmedSource>> confirmed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeliveryConfirmedSource> confirmedBags = new ConcurrentHashMap<>();

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

    @Override
    public Optional<DeliveryDraft> lock(String deliveryId, WorkSession session) {
        return find(deliveryId);
    }

    @Override
    public void confirm(DeliveryDraft next, List<DeliveryConfirmedSource> sources, int expectedVersion, WorkSession session) {
        int index = indexOf(next.deliveryId());
        if (index < 0) {
            throw new BusinessException("交付草稿不存在");
        }
        DeliveryDraft current = headers.get(index);
        if (current.version() != expectedVersion) {
            throw new BusinessException("交付草稿已被其他人修改，请刷新后重试");
        }
        if (current.status() != DeliveryDraftStatus.DRAFT) {
            throw new BusinessException("该交付单已经确认");
        }
        List<DeliveryConfirmedSource> stored = List.copyOf(sources);
        for (DeliveryConfirmedSource source : stored) {
            if (confirmedBags.containsKey(source.bagId())) {
                throw new BusinessException("该袋已经交付");
            }
        }
        headers.set(index, next);
        confirmed.put(next.deliveryId(), stored);
        for (DeliveryConfirmedSource source : stored) {
            confirmedBags.put(source.bagId(), source);
        }
        if (session != null) {
            session.onRollback(() -> {
                headers.set(index, current);
                confirmed.remove(next.deliveryId(), stored);
                for (DeliveryConfirmedSource source : stored) {
                    confirmedBags.remove(source.bagId(), source);
                }
            });
        }
    }

    @Override
    public void abandon(DeliveryDraft abandoned, int expectedVersion, WorkSession session) {
        int index = indexOf(abandoned.deliveryId());
        if (index < 0) {
            throw new BusinessException("交付草稿不存在");
        }
        DeliveryDraft current = headers.get(index);
        if (current.version() != expectedVersion) {
            throw new BusinessException("交付草稿已被其他人修改，请刷新后重试");
        }
        if (current.status() != DeliveryDraftStatus.DRAFT) {
            throw new BusinessException("已确认交付不能当作草稿作废");
        }
        headers.set(index, abandoned);
        if (session != null) {
            int at = index;
            session.onRollback(() -> headers.set(at, current));
        }
    }

    @Override
    public void reverse(DeliveryDraft reversed, int expectedVersion, WorkSession session) {
        int index = indexOf(reversed.deliveryId());
        if (index < 0) {
            throw new BusinessException("交付草稿不存在");
        }
        DeliveryDraft current = headers.get(index);
        if (current.version() != expectedVersion || current.status() != DeliveryDraftStatus.CONFIRMED) {
            throw new BusinessException("交付草稿已被其他人修改，请刷新后重试");
        }
        List<DeliveryConfirmedSource> sources = confirmed.getOrDefault(reversed.deliveryId(), List.of());
        headers.set(index, reversed);
        for (DeliveryConfirmedSource source : sources) {
            confirmedBags.remove(source.bagId());
        }
        if (session != null) {
            int at = index;
            session.onRollback(() -> {
                headers.set(at, current);
                for (DeliveryConfirmedSource source : sources) {
                    confirmedBags.put(source.bagId(), source);
                }
            });
        }
    }

    @Override
    public List<DeliveryConfirmedSource> confirmedSources(String deliveryId) {
        return confirmed.getOrDefault(deliveryId, List.of()).stream()
                .sorted(Comparator.comparingInt(DeliveryConfirmedSource::lineNo))
                .toList();
    }

    @Override
    public boolean bagConfirmed(String bagId) {
        return bagId != null && confirmedBags.containsKey(bagId);
    }

    private int indexOf(String deliveryId) {
        for (int index = 0; index < headers.size(); index++) {
            if (headers.get(index).deliveryId().equals(deliveryId)) {
                return index;
            }
        }
        return -1;
    }
}
