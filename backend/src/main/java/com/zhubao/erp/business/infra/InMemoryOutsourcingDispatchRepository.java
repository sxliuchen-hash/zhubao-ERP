package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-dispatch.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOutsourcingDispatchRepository implements OutsourcingDispatchRepository {

    private final CopyOnWriteArrayList<OutsourcingDispatch> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(OutsourcingDispatch dispatch, WorkSession session) {
        if (rows.stream().anyMatch(row -> row.dispatchId().equals(dispatch.dispatchId())
                || row.requestId().equals(dispatch.requestId())
                || row.sentBagId().equals(dispatch.sentBagId()))) {
            throw new BusinessException("该袋已发外");
        }
        rows.add(dispatch);
        if (session != null) {
            session.onRollback(() -> rows.remove(dispatch));
        }
    }

    @Override
    public Optional<OutsourcingDispatch> find(String dispatchId) {
        return rows.stream().filter(row -> row.dispatchId().equals(dispatchId)).findFirst();
    }

    @Override
    public Optional<OutsourcingDispatch> findByRequest(String requestId) {
        return rows.stream().filter(row -> row.requestId().equals(requestId)).findFirst();
    }

    @Override
    public Optional<OutsourcingDispatch> findBySentBag(String bagId) {
        return rows.stream().filter(row -> row.sentBagId().equals(bagId)).findFirst();
    }

    @Override
    public List<OutsourcingDispatch> listByTask(String taskId) {
        return rows.stream().filter(row -> row.taskId().equals(taskId))
                .sorted(Comparator.comparing(OutsourcingDispatch::dispatchId)).toList();
    }

    @Override
    public List<OutsourcingDispatch> listByParty(String partyId) {
        return rows.stream().filter(row -> row.partyId().equals(partyId))
                .sorted(Comparator.comparing(OutsourcingDispatch::dispatchId)).toList();
    }
}
