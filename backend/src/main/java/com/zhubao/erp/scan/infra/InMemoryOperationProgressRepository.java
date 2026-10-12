package com.zhubao.erp.scan.infra;

import com.zhubao.erp.scan.domain.OperationProgressRecord;
import com.zhubao.erp.scan.domain.OperationProgressRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.scan-progress.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryOperationProgressRepository implements OperationProgressRepository {

    private final List<OperationProgressRecord> records = new ArrayList<>();

    @Override
    public synchronized void insert(OperationProgressRecord record) {
        boolean duplicate = records.stream().anyMatch(existing ->
                existing.code().equals(record.code()) || sameReplay(existing, record));
        if (duplicate) {
            throw new IllegalStateException("过程记录已存在");
        }
        records.add(record);
    }

    @Override
    public synchronized Optional<OperationProgressRecord> findByCode(String code) {
        return records.stream().filter(record -> record.code().equals(code)).findFirst();
    }

    @Override
    public synchronized List<OperationProgressRecord> findByRequestId(String requestId) {
        return records.stream().filter(record -> record.requestId().equals(requestId)).toList();
    }

    @Override
    public synchronized List<OperationProgressRecord> findByTarget(String targetType, String targetCode) {
        return records.stream()
                .filter(record -> record.targetType().equals(targetType) && record.targetCode().equals(targetCode))
                .toList();
    }

    @Override
    public synchronized Optional<OperationProgressRecord> findReplay(
            String requestId,
            String operatorId,
            String targetType,
            String targetCode,
            String contentHash
    ) {
        return records.stream()
                .filter(record -> requestId.equals(record.requestId())
                        && operatorId.equals(record.operatorId())
                        && targetType.equals(record.targetType())
                        && targetCode.equals(record.targetCode())
                        && contentHash.equals(record.contentHash()))
                .findFirst();
    }

    private boolean sameReplay(OperationProgressRecord left, OperationProgressRecord right) {
        return left.requestId().equals(right.requestId())
                && left.operatorId().equals(right.operatorId())
                && left.targetType().equals(right.targetType())
                && left.targetCode().equals(right.targetCode())
                && left.contentHash().equals(right.contentHash());
    }
}
