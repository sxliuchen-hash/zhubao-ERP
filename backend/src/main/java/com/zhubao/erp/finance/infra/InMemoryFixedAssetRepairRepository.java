package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetRepair;
import com.zhubao.erp.finance.domain.FixedAssetRepairRepository;
import com.zhubao.erp.finance.domain.FixedAssetRepairState;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-repair.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFixedAssetRepairRepository implements FixedAssetRepairRepository {

    private final ConcurrentHashMap<String, FixedAssetRepair> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(FixedAssetRepair repair, WorkSession session) {
        if (rows.putIfAbsent(repair.repairId(), repair) != null) {
            throw new BusinessException("维修记录已存在");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(repair.repairId(), repair));
        }
    }

    @Override
    public Optional<FixedAssetRepair> find(String repairId) {
        return Optional.ofNullable(rows.get(repairId));
    }

    @Override
    public Optional<FixedAssetRepair> lock(String repairId, WorkSession session) {
        return find(repairId);
    }

    @Override
    public void confirm(String repairId, String evidenceCode, String payableCode, WorkSession session) {
        FixedAssetRepair current = rows.get(repairId);
        if (current == null || current.state() != FixedAssetRepairState.RECORDED || !current.payableCode().isEmpty()) {
            throw new BusinessException("维修费用已经确认");
        }
        if (rows.values().stream().anyMatch(row -> payableCode.equals(row.payableCode()))) {
            throw new BusinessException("这笔应付已经对应过维修");
        }
        FixedAssetRepair confirmed = new FixedAssetRepair(
                current.repairId(), current.assetCode(), current.repairedOn(), current.handoverName(),
                evidenceCode, FixedAssetRepairState.CONFIRMED, payableCode
        );
        if (!rows.replace(repairId, current, confirmed)) {
            throw new BusinessException("维修费用已经确认");
        }
        if (session != null) {
            session.onRollback(() -> rows.put(repairId, current));
        }
    }

    @Override
    public List<FixedAssetRepair> list(String assetCode) {
        return rows.values().stream()
                .filter(row -> assetCode.equals(row.assetCode()))
                .sorted(Comparator.comparing(FixedAssetRepair::repairId))
                .toList();
    }
}
