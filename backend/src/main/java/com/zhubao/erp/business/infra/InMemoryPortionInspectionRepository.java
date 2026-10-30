package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionInspectionRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.portion-inspection.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryPortionInspectionRepository implements PortionInspectionRepository {

    private final List<PortionInspection> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(PortionInspection inspection, WorkSession session) {
        if (rows.stream().anyMatch(row -> row.requestId().equals(inspection.requestId()))) {
            throw new BusinessException("质量检查已存在");
        }
        rows.add(inspection);
        if (session != null) {
            session.onRollback(() -> rows.remove(inspection));
        }
    }

    @Override
    public List<PortionInspection> listByBag(String bagId) {
        return rows.stream().filter(row -> row.bagId().equals(bagId)).toList();
    }

    public List<PortionInspection> all() {
        return new ArrayList<>(rows);
    }
}
