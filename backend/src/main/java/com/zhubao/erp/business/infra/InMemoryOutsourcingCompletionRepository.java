package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OutsourcingCompletion;
import com.zhubao.erp.business.domain.OutsourcingCompletionRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-completion.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOutsourcingCompletionRepository implements OutsourcingCompletionRepository {

    private final CopyOnWriteArrayList<OutsourcingCompletion> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(OutsourcingCompletion completion, WorkSession session) {
        if (rows.stream().anyMatch(row -> row.completionId().equals(completion.completionId())
                || row.requestId().equals(completion.requestId())
                || row.returnId().equals(completion.returnId()))) {
            throw new BusinessException("外协完成量已记录");
        }
        rows.add(completion);
        if (session != null) {
            session.onRollback(() -> rows.remove(completion));
        }
    }

    @Override
    public Optional<OutsourcingCompletion> find(String completionId) {
        return rows.stream().filter(row -> row.completionId().equals(completionId)).findFirst();
    }

    @Override
    public Optional<OutsourcingCompletion> findByReturn(String returnId) {
        return rows.stream().filter(row -> row.returnId().equals(returnId)).findFirst();
    }
}
