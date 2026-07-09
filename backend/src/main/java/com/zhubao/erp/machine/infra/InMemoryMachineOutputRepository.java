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

    @Override
    public int bindMachineId(String machineNoSnapshot, Long machineId) {
        int bound = 0;
        for (MachineOutputRecord record : records.values()) {
            // 仅绑定同编号且尚未绑定机器档案 ID 的记录，避免覆盖历史绑定
            if (machineNoSnapshot.equals(record.machineNoSnapshot()) && record.machineId() == null) {
                records.put(record.code(), withMachineId(record, machineId));
                bound++;
            }
        }
        return bound;
    }

    // 记录不可变，绑定机器档案 ID 时重建一条记录替换原记录
    private MachineOutputRecord withMachineId(MachineOutputRecord record, Long machineId) {
        return new MachineOutputRecord(
                record.code(), record.workDate(), record.customerId(), record.customerNameSnapshot(),
                record.productCategory(), machineId, record.machineNoSnapshot(), record.workOrderCode(),
                record.workProcessId(), record.workReportCode(), record.outputQuantity(), record.quantityUnit(),
                record.operatorEmployeeId(), record.operatorSignatureName(), record.signatureMediaId(),
                record.sourceRegisterType(), record.sourceLineNo(), record.machineLineNo(),
                record.sourceSequenceNo(), record.importBatchNo(), record.ledgerCode(), record.status(),
                record.remark(), record.createdBy(), record.createdAt()
        );
    }
}
