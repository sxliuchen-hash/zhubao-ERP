package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetAcquisition;
import com.zhubao.erp.finance.domain.FixedAssetAcquisitionRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-acquisition.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFixedAssetAcquisitionRepository implements FixedAssetAcquisitionRepository {

    private final ConcurrentHashMap<String, FixedAssetAcquisition> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(FixedAssetAcquisition acquisition, WorkSession session) {
        if (occupied(acquisition)) {
            throw new BusinessException("这笔应付或资产已经有购置来源");
        }
        FixedAssetAcquisition previous = rows.putIfAbsent(acquisition.acquisitionId(), acquisition);
        if (previous != null) {
            throw new BusinessException("购置来源已存在");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(acquisition.acquisitionId(), acquisition));
        }
    }

    @Override
    public Optional<FixedAssetAcquisition> find(String acquisitionId) {
        return Optional.ofNullable(rows.get(acquisitionId));
    }

    @Override
    public Optional<FixedAssetAcquisition> lockByAsset(String assetCode, WorkSession session) {
        if (assetCode == null || assetCode.isBlank()) {
            return Optional.empty();
        }
        return rows.values().stream().filter(row -> assetCode.equals(row.assetCode())).findFirst();
    }

    @Override
    public boolean coversPayable(String payableCode, WorkSession session) {
        if (payableCode == null || payableCode.isBlank()) {
            return false;
        }
        return rows.values().stream().anyMatch(row ->
                payableCode.equals(row.payableCode()) || payableCode.equals(row.coveredPayableCode()));
    }

    @Override
    public List<FixedAssetAcquisition> list() {
        return rows.values().stream()
                .sorted(Comparator.comparing(FixedAssetAcquisition::acquisitionId))
                .toList();
    }

    private boolean occupied(FixedAssetAcquisition acquisition) {
        return lockByAsset(acquisition.assetCode(), null).isPresent()
                || coversPayable(acquisition.payableCode(), null)
                || coversPayable(acquisition.coveredPayableCode(), null);
    }
}
