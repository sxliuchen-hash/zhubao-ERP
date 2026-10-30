package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.CustomerReturnRepository;
import com.zhubao.erp.business.domain.CustomerReturnSource;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.customer-return.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCustomerReturnRepository implements CustomerReturnRepository {

    private final CopyOnWriteArrayList<CustomerReturnSource> rows = new CopyOnWriteArrayList<>();

    @Override
    public void save(CustomerReturnSource source, WorkSession session) {
        if (rows.stream().anyMatch(row -> row.returnId().equals(source.returnId())
                || row.requestId().equals(source.requestId()))) {
            throw new BusinessException("退货来源已存在");
        }
        rows.add(source);
        if (session != null) {
            session.onRollback(() -> rows.remove(source));
        }
    }

    @Override
    public Optional<CustomerReturnSource> find(String returnId) {
        return rows.stream().filter(row -> row.returnId().equals(returnId)).findFirst();
    }

    @Override
    public Optional<CustomerReturnSource> findByRequest(String requestId) {
        return rows.stream().filter(row -> row.requestId().equals(requestId)).findFirst();
    }

    @Override
    public List<CustomerReturnSource> listByOutbound(String outboundCode) {
        return rows.stream().filter(row -> row.outboundCode().equals(outboundCode))
                .sorted(Comparator.comparing(CustomerReturnSource::returnId))
                .toList();
    }
}
