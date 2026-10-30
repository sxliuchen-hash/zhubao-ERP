package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.FactoryItemRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.factory-item.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFactoryItemRepository implements FactoryItemRepository {

    private final ConcurrentHashMap<String, FactoryItem> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(FactoryItem item, WorkSession session) {
        synchronized (rows) {
            if (findIdentity(item.category(), item.materialName(), item.specCode(), item.unit()).isPresent()) {
                throw new BusinessException("同一类别、材质、规格和单位已经建档，不能合并");
            }
            FactoryItem previous = rows.putIfAbsent(item.itemId(), item);
            if (previous != null) {
                throw new BusinessException("物品档案编号已存在");
            }
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(item.itemId(), item));
        }
    }

    @Override
    public void updatePolicy(FactoryItem next, int expectedVersion, WorkSession session) {
        FactoryItem current;
        synchronized (rows) {
            current = rows.get(next.itemId());
            if (current == null) {
                throw new BusinessException("物品档案不存在");
            }
            if (current.version() != expectedVersion || !rows.replace(next.itemId(), current, next)) {
                throw new BusinessException("物品档案已被其他人修改，请刷新后重试");
            }
        }
        if (session != null) {
            session.onRollback(() -> rows.put(next.itemId(), current));
        }
    }

    @Override
    public Optional<FactoryItem> find(String itemId) {
        return Optional.ofNullable(rows.get(itemId));
    }

    @Override
    public Optional<FactoryItem> findIdentity(FactoryItemCategory category, String materialName, String specCode, String unit) {
        return rows.values().stream()
                .filter(item -> item.category() == category
                        && item.materialName().equals(materialName)
                        && item.specCode().equals(specCode)
                        && item.unit().equals(unit))
                .findFirst();
    }

    @Override
    public List<FactoryItem> list(String materialName) {
        String name = materialName == null ? "" : materialName.trim();
        return rows.values().stream()
                .filter(item -> name.isEmpty() || item.materialName().equals(name))
                .sorted(Comparator.comparing(FactoryItem::itemId))
                .toList();
    }
}
