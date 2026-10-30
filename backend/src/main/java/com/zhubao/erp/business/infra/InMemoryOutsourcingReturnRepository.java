package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingReturnRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.outsourcing-return.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOutsourcingReturnRepository implements OutsourcingReturnRepository {

    private final CopyOnWriteArrayList<OutsourcingReturn> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(OutsourcingReturn row, WorkSession session) {
        if (rows.stream().anyMatch(item -> item.returnId().equals(row.returnId()) || item.requestId().equals(row.requestId()))) {
            throw new BusinessException("外协收回已存在");
        }
        rows.add(row);
        if (session != null) {
            session.onRollback(() -> rows.remove(row));
        }
    }

    @Override
    public Optional<OutsourcingReturn> find(String returnId) {
        return rows.stream().filter(row -> row.returnId().equals(returnId)).findFirst();
    }

    @Override
    public Optional<OutsourcingReturn> findByRequest(String requestId) {
        return rows.stream().filter(row -> row.requestId().equals(requestId)).findFirst();
    }

    @Override
    public List<OutsourcingReturn> listByDispatch(String dispatchId) {
        return rows.stream().filter(row -> row.dispatchId().equals(dispatchId))
                .sorted(Comparator.comparingInt(OutsourcingReturn::dispatchVersion)).toList();
    }

    @Override
    public boolean inHouse(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            return false;
        }
        return rows.stream().anyMatch(row -> bagId.equals(row.receivedBagId()) || bagId.equals(row.unprocessedBagId()));
    }

    @Override
    public Optional<String> currentOutParty(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            return Optional.empty();
        }
        return rows.stream()
                .filter(row -> bagId.equals(row.remainderBagId()))
                .filter(row -> row.dispatchVersion() == maxVersion(row.dispatchId()))
                .map(OutsourcingReturn::partyId)
                .findFirst();
    }

    private int maxVersion(String dispatchId) {
        return listByDispatch(dispatchId).stream().mapToInt(OutsourcingReturn::dispatchVersion).max().orElse(0);
    }
}
