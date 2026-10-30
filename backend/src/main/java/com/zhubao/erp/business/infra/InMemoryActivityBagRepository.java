package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.activity-bag.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryActivityBagRepository implements ActivityBagRepository {

    private final ConcurrentHashMap<String, ActivityBag> bags = new ConcurrentHashMap<>();

    @Override
    public void save(ActivityBag bag, int expectedVersion, WorkSession session) {
        ActivityBag previous = bags.get(bag.id());
        int current = previous == null ? 0 : previous.version();
        if (current != expectedVersion) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        bags.put(bag.id(), bag);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    bags.remove(bag.id(), bag);
                } else {
                    bags.put(bag.id(), previous);
                }
            });
        }
    }

    @Override
    public Optional<ActivityBag> find(String id) {
        return Optional.ofNullable(bags.get(id));
    }

    @Override
    public List<ActivityBag> listByTarget(String targetCode) {
        return bags.values().stream().filter(bag -> bag.targetCode().equals(targetCode)).toList();
    }
}
