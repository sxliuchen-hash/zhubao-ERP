package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.QuantityDispositionLine;
import com.zhubao.erp.business.domain.QuantityDispositionRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.quantity-disposition.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryQuantityDispositionRepository implements QuantityDispositionRepository {

    private final List<QuantityDispositionLine> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(QuantityDispositionLine line, WorkSession session) {
        if (rows.stream().anyMatch(row -> row.requestId().equals(line.requestId()))) {
            throw new BusinessException("数量处置已存在");
        }
        rows.add(line);
        if (session != null) {
            session.onRollback(() -> rows.remove(line));
        }
    }

    @Override
    public List<QuantityDispositionLine> listByBag(String bagId) {
        return rows.stream().filter(row -> row.bagId().equals(bagId)).toList();
    }

    public List<QuantityDispositionLine> all() {
        return new ArrayList<>(rows);
    }
}
