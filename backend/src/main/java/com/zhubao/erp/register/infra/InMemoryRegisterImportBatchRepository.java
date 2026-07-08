package com.zhubao.erp.register.infra;

import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportBatchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "zhubao.register-import.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryRegisterImportBatchRepository implements RegisterImportBatchRepository {

    private final ConcurrentMap<String, RegisterImportBatch> batches = new ConcurrentHashMap<>();

    @Override
    public RegisterImportBatch save(RegisterImportBatch batch) {
        batches.put(batch.importBatchNo(), batch);
        return batch;
    }

    @Override
    public RegisterImportBatch updateRowFields(
            String importBatchNo,
            int sourceLineNo,
            Map<String, String> normalizedFields,
            String nextAction
    ) {
        RegisterImportBatch current = batches.get(importBatchNo);
        if (current == null) {
            return null;
        }
        List<com.zhubao.erp.register.domain.RegisterImportBatchRow> rows = current.rows().stream()
                .map(row -> row.sourceLineNo() == sourceLineNo
                        ? new com.zhubao.erp.register.domain.RegisterImportBatchRow(
                                row.importBatchNo(),
                                row.sourceLineNo(),
                                row.sourceRegisterSequence(),
                                row.status(),
                                normalizedFields,
                                row.messages()
                        )
                        : row)
                .toList();
        boolean found = rows.stream().anyMatch(row -> row.sourceLineNo() == sourceLineNo);
        if (!found) {
            return null;
        }
        RegisterImportBatch updated = new RegisterImportBatch(
                current.importBatchNo(),
                current.templateCode(),
                current.templateName(),
                current.sourceFileName(),
                current.targetCommand(),
                current.totalRows(),
                current.readyRows(),
                current.warningRows(),
                current.blockedRows(),
                current.executable(),
                current.status(),
                current.confirmedBy(),
                current.confirmedAt(),
                current.confirmRemark(),
                nextAction,
                rows
        );
        batches.put(importBatchNo, updated);
        return updated;
    }

    @Override
    public RegisterImportBatch markRowsPosted(
            String importBatchNo,
            List<Integer> sourceLineNos,
            boolean batchPosted,
            String nextAction
    ) {
        RegisterImportBatch current = batches.get(importBatchNo);
        if (current == null) {
            return null;
        }
        Set<Integer> postedLines = sourceLineNos.stream().collect(Collectors.toSet());
        List<com.zhubao.erp.register.domain.RegisterImportBatchRow> rows = current.rows().stream()
                .map(row -> postedLines.contains(row.sourceLineNo())
                        ? new com.zhubao.erp.register.domain.RegisterImportBatchRow(
                                row.importBatchNo(),
                                row.sourceLineNo(),
                                row.sourceRegisterSequence(),
                                "POSTED",
                                row.normalizedFields(),
                                row.messages()
                        )
                        : row)
                .toList();
        RegisterImportBatch updated = new RegisterImportBatch(
                current.importBatchNo(),
                current.templateCode(),
                current.templateName(),
                current.sourceFileName(),
                current.targetCommand(),
                current.totalRows(),
                current.readyRows(),
                current.warningRows(),
                current.blockedRows(),
                current.executable(),
                batchPosted ? "POSTED" : current.status(),
                current.confirmedBy(),
                current.confirmedAt(),
                current.confirmRemark(),
                nextAction,
                rows
        );
        batches.put(importBatchNo, updated);
        return updated;
    }

    @Override
    public Optional<RegisterImportBatch> findByBatchNo(String importBatchNo) {
        return Optional.ofNullable(batches.get(importBatchNo));
    }

    @Override
    public List<RegisterImportBatch> findRecent(int limit) {
        return batches.values().stream()
                .sorted(Comparator.comparing(RegisterImportBatch::confirmedAt).reversed())
                .limit(limit)
                .toList();
    }
}
