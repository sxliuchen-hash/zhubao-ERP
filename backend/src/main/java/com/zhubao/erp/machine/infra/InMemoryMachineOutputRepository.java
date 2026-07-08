package com.zhubao.erp.machine.infra;

import com.zhubao.erp.machine.domain.MachineOutputRecord;
import com.zhubao.erp.machine.domain.MachineOutputRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "zhubao.machine-output.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryMachineOutputRepository implements MachineOutputRepository {

    private final ConcurrentMap<String, MachineOutputRecord> records = new ConcurrentHashMap<>();

    @Override
    public MachineOutputRecord save(MachineOutputRecord record) {
        records.put(record.code(), record);
        return record;
    }

    @Override
    public Optional<MachineOutputRecord> findByCode(String code) {
        return Optional.ofNullable(records.get(code));
    }

    @Override
    public List<MachineOutputRecord> findRecent(int limit) {
        return records.values().stream()
                .sorted(Comparator.comparing(MachineOutputRecord::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<MachineOutputRecord> findByImportBatchNo(String importBatchNo) {
        return records.values().stream()
                .filter(record -> importBatchNo.equals(record.importBatchNo()))
                .sorted(Comparator.comparing(MachineOutputRecord::sourceLineNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }
}
