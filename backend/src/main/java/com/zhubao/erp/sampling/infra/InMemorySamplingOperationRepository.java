package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingMakingChoice;
import com.zhubao.erp.sampling.domain.SamplingOperationReport;
import com.zhubao.erp.sampling.domain.SamplingOperationRepository;
import com.zhubao.erp.sampling.domain.SamplingVersionKind;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-operation.repository", havingValue = "memory", matchIfMissing = true)
public class InMemorySamplingOperationRepository implements SamplingOperationRepository {

    private final ConcurrentHashMap<String, SamplingOperationReport> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(SamplingOperationReport report, WorkSession session) {
        if (photoUsed(report.attachmentCode(), session)) {
            throw new BusinessException("这张照片已经用于其他业务，不能当作本次拍摄");
        }
        SamplingOperationReport previous = rows.putIfAbsent(report.reportId(), report);
        if (previous != null) {
            throw new BusinessException("报工编号冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(report.reportId(), report));
        }
    }

    @Override
    public Optional<SamplingOperationReport> find(String reportId) {
        if (reportId == null || reportId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(reportId.trim()));
    }

    @Override
    public boolean photoUsed(String attachmentCode, WorkSession session) {
        if (attachmentCode == null || attachmentCode.isBlank()) {
            return false;
        }
        return rows.values().stream().anyMatch(report -> attachmentCode.equals(report.attachmentCode()));
    }

    @Override
    public List<SamplingOperationReport> list(String samplingCode) {
        return rows.values().stream()
                .filter(report -> report.samplingCode().equals(samplingCode))
                .sorted(Comparator.comparing(SamplingOperationReport::reportId))
                .toList();
    }
}
