package com.zhubao.erp.finance.infra;

import com.zhubao.erp.finance.domain.FixedAssetCustodyRepository;
import com.zhubao.erp.finance.domain.FixedAssetCustodyTransfer;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-custody.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFixedAssetCustodyRepository implements FixedAssetCustodyRepository {

    private final CopyOnWriteArrayList<FixedAssetCustodyTransfer> rows = new CopyOnWriteArrayList<>();

    @Override
    public void insert(FixedAssetCustodyTransfer transfer, WorkSession session) {
        rows.add(transfer);
        if (session != null) {
            session.onRollback(() -> rows.remove(transfer));
        }
    }

    @Override
    public List<FixedAssetCustodyTransfer> list(String assetCode) {
        return rows.stream()
                .filter(row -> assetCode.equals(row.assetCode()))
                .sorted(Comparator.comparingLong(FixedAssetCustodyTransfer::version))
                .toList();
    }
}
