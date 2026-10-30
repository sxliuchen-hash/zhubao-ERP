package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasis;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasisRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-fee-basis.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOutsourcingFeeBasisRepository implements OutsourcingFeeBasisRepository {

    private final CopyOnWriteArrayList<OutsourcingFeeBasis> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(OutsourcingFeeBasis basis, WorkSession session) {
        if (rows.stream().anyMatch(row -> row.basisId().equals(basis.basisId())
                || row.completionId().equals(basis.completionId()))) {
            throw new BusinessException("外协费用依据已存在");
        }
        rows.add(basis);
        if (session != null) {
            session.onRollback(() -> rows.remove(basis));
        }
    }

    @Override
    public Optional<OutsourcingFeeBasis> findByCompletion(String completionId) {
        return rows.stream().filter(row -> row.completionId().equals(completionId)).findFirst();
    }
}
