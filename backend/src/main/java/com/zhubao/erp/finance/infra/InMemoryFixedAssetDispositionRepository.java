package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetDisposition;
import com.zhubao.erp.finance.domain.FixedAssetDispositionRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-disposition.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFixedAssetDispositionRepository implements FixedAssetDispositionRepository {

    private final CopyOnWriteArrayList<FixedAssetDisposition> rows = new CopyOnWriteArrayList<>();

    @Override
    public void insert(FixedAssetDisposition disposition, WorkSession session) {
        if (coversReceipt(disposition.receiptCode(), session)) {
            throw new BusinessException("这笔回款已经对应过处置");
        }
        rows.add(disposition);
        if (session != null) {
            session.onRollback(() -> rows.remove(disposition));
        }
    }

    @Override
    public boolean coversReceipt(String receiptCode, WorkSession session) {
        if (receiptCode == null || receiptCode.isBlank()) {
            return false;
        }
        return rows.stream().anyMatch(row -> receiptCode.equals(row.receiptCode()));
    }

    @Override
    public List<FixedAssetDisposition> list(String assetCode) {
        return rows.stream()
                .filter(row -> assetCode.equals(row.assetCode()))
                .sorted(Comparator.comparing(FixedAssetDisposition::dispositionId))
                .toList();
    }
}
