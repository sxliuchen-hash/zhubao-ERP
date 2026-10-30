package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.WholeBagReport;
import com.zhubao.erp.business.domain.WholeBagReportRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.whole-bag-report.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryWholeBagReportRepository implements WholeBagReportRepository {

    private final ConcurrentHashMap<String, WholeBagReport> byRequest = new ConcurrentHashMap<>();

    @Override
    public void save(WholeBagReport report, WorkSession session) {
        WholeBagReport previous = byRequest.putIfAbsent(report.requestId(), report);
        if (previous != null) {
            throw new BusinessException("整袋报工批次已存在");
        }
        if (session != null) {
            session.onRollback(() -> byRequest.remove(report.requestId(), report));
        }
    }

    @Override
    public Optional<WholeBagReport> findByRequest(String requestId) {
        return Optional.ofNullable(byRequest.get(requestId));
    }
}
