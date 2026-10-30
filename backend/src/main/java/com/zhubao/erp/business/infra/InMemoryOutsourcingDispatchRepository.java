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
    public Optional<OutsourcingDispatch> lock(String dispatchId, WorkSession session) {
        return find(dispatchId);
    }

    @Override
    public void update(OutsourcingDispatch next, int expectedVersion, WorkSession session) {
        int index = indexOf(next.dispatchId());
        if (index < 0) {
            throw new BusinessException("外协发外不存在");
        }
        OutsourcingDispatch current = rows.get(index);
        if (current.version() != expectedVersion) {
            throw new BusinessException("外协发外已被其他人修改，请刷新后重试");
        }
        rows.set(index, next);
        if (session != null) {
            int at = index;
            session.onRollback(() -> rows.set(at, current));
        }
    }

    @Override
    public Optional<OutsourcingDispatch> find(String dispatchId) {
        return rows.stream().filter(row -> row.dispatchId().equals(dispatchId)).findFirst();
    }

    private int indexOf(String dispatchId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).dispatchId().equals(dispatchId)) {
                return i;
            }
        }
        return -1;
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
