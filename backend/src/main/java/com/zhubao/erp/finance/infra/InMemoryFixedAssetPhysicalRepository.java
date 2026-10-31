package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetPhysical;
import com.zhubao.erp.finance.domain.FixedAssetPhysicalRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-card.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFixedAssetPhysicalRepository implements FixedAssetPhysicalRepository {

    private final ConcurrentHashMap<String, FixedAssetPhysical> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<FixedAssetPhysical> find(String assetCode) {
        return Optional.ofNullable(rows.get(assetCode));
    }

    @Override
    public Optional<FixedAssetPhysical> lock(String assetCode, WorkSession session) {
        return find(assetCode);
    }

    @Override
    public void insert(FixedAssetPhysical row, WorkSession session) {
        FixedAssetPhysical previous = rows.putIfAbsent(row.assetCode(), row);
        if (previous != null) {
            throw new BusinessException("旧卡已接续，不能重建");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(row.assetCode(), row));
        }
    }

    @Override
    public void replace(FixedAssetPhysical row, long expectedVersion, WorkSession session) {
        FixedAssetPhysical previous = rows.get(row.assetCode());
        if (previous == null || previous.version() != expectedVersion) {
            throw new BusinessException("资产卡片版本已变化");
        }
        if (!rows.replace(row.assetCode(), previous, row)) {
            throw new BusinessException("资产卡片版本已变化");
        }
        if (session != null) {
            session.onRollback(() -> rows.put(row.assetCode(), previous));
        }
    }
}
