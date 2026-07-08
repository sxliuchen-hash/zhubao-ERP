package com.zhubao.erp.machine.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.machine.domain.MachineOutputMachineSummary;
import com.zhubao.erp.machine.domain.MachineOutputRecord;
import com.zhubao.erp.machine.domain.MachineOutputRepository;
import com.zhubao.erp.machine.domain.MachineOutputSummary;
import com.zhubao.erp.machine.domain.MachineOutputWorkDateSummary;
import com.zhubao.erp.machine.domain.SubmitMachineOutputCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MachineOutputService {

    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CREATED_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final MachineOutputRepository repository;

    public MachineOutputService(MachineOutputRepository repository) {
        this.repository = repository;
    }

    public MachineOutputRecord submit(SubmitMachineOutputCommand command) {
        validate(command);
        String code = normalizeCode(command);
        repository.findByCode(code).ifPresent(existing -> {
            throw new BusinessException("设备产量记录已存在：" + existing.code());
        });
        String createdAt = CREATED_TIME_FORMATTER.format(LocalDateTime.now());
        MachineOutputRecord record = new MachineOutputRecord(
                code,
                command.workDate().trim(),
                command.customerId(),
                blankToEmpty(command.customerNameSnapshot()),
                command.productCategory().trim(),
                command.machineId(),
                command.machineNoSnapshot().trim(),
                blankToEmpty(command.workOrderCode()),
                command.workProcessId(),
                blankToEmpty(command.workReportCode()),
                command.outputQuantity(),
                blankToDefault(command.quantityUnit(), "件"),
                command.operatorEmployeeId(),
                command.operatorSignatureName().trim(),
                command.signatureMediaId(),
                blankToEmpty(command.sourceRegisterType()),
                command.sourceLineNo(),
                blankToEmpty(command.machineLineNo()),
                blankToEmpty(command.sourceSequenceNo()),
                blankToEmpty(command.importBatchNo()),
                blankToEmpty(command.ledgerCode()),
                blankToDefault(command.status(), "SUBMITTED"),
                blankToEmpty(command.remark()),
                blankToDefault(command.operator(), "设备产量录入员"),
                createdAt
        );
        return repository.save(record);
    }

    public List<MachineOutputRecord> recent(int limit) {
        return repository.findRecent(Math.max(1, Math.min(limit, 100)));
    }

    public MachineOutputSummary summary(int limit) {
        List<MachineOutputRecord> records = recent(limit);
        BigDecimal totalQuantity = records.stream()
                .map(this::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MachineOutputSummary(
                records.size(),
                totalQuantity,
                resolveQuantityUnit(records),
                summarizeByMachine(records),
                summarizeByWorkDate(records),
                (int) records.stream().filter(record -> record.machineId() == null).count(),
                (int) records.stream().filter(record -> record.signatureMediaId() == null).count()
        );
    }

    public List<MachineOutputRecord> byImportBatchNo(String importBatchNo) {
        if (importBatchNo == null || importBatchNo.isBlank()) {
            throw new BusinessException("缺少导入批次号");
        }
        return repository.findByImportBatchNo(importBatchNo.trim());
    }

    private void validate(SubmitMachineOutputCommand command) {
        if (command == null) {
            throw new BusinessException("设备产量提交命令不能为空");
        }
        require(command.workDate(), "workDate");
        require(command.productCategory(), "productCategory");
        require(command.machineNoSnapshot(), "machineNoSnapshot");
        require(command.operatorSignatureName(), "operatorSignatureName");
        if (command.outputQuantity() == null || command.outputQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("outputQuantity 必须大于 0");
        }
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " 不能为空");
        }
    }

    private String normalizeCode(SubmitMachineOutputCommand command) {
        if (command.code() != null && !command.code().isBlank()) {
            return command.code().trim();
        }
        String suffix = command.sourceLineNo() == null ? "0" : String.valueOf(command.sourceLineNo());
        return "MOR-" + CODE_TIME_FORMATTER.format(LocalDateTime.now()) + "-" + suffix;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private List<MachineOutputMachineSummary> summarizeByMachine(List<MachineOutputRecord> records) {
        Map<String, QuantityAccumulator> summaries = new LinkedHashMap<>();
        for (MachineOutputRecord record : records) {
            String machineNo = blankToDefault(record.machineNoSnapshot(), "未记录机器");
            summaries.computeIfAbsent(machineNo, ignored -> new QuantityAccumulator()).add(quantity(record));
        }
        return summaries.entrySet().stream()
                .map(entry -> new MachineOutputMachineSummary(entry.getKey(), entry.getValue().quantity, entry.getValue().records))
                .sorted(Comparator
                        .comparing(MachineOutputMachineSummary::quantity).reversed()
                        .thenComparing(MachineOutputMachineSummary::machineNoSnapshot))
                .toList();
    }

    private List<MachineOutputWorkDateSummary> summarizeByWorkDate(List<MachineOutputRecord> records) {
        Map<String, QuantityAccumulator> summaries = new LinkedHashMap<>();
        for (MachineOutputRecord record : records) {
            String workDate = blankToDefault(record.workDate(), "未记录日期");
            summaries.computeIfAbsent(workDate, ignored -> new QuantityAccumulator()).add(quantity(record));
        }
        return summaries.entrySet().stream()
                .map(entry -> new MachineOutputWorkDateSummary(entry.getKey(), entry.getValue().quantity, entry.getValue().records))
                .sorted(Comparator.comparing(MachineOutputWorkDateSummary::workDate).reversed())
                .toList();
    }

    private BigDecimal quantity(MachineOutputRecord record) {
        return record.outputQuantity() == null ? BigDecimal.ZERO : record.outputQuantity();
    }

    private String resolveQuantityUnit(List<MachineOutputRecord> records) {
        List<String> units = records.stream()
                .map(record -> blankToDefault(record.quantityUnit(), "件"))
                .distinct()
                .toList();
        if (units.isEmpty()) {
            return "件";
        }
        return units.size() == 1 ? units.get(0) : "混合";
    }

    private static class QuantityAccumulator {
        private BigDecimal quantity = BigDecimal.ZERO;
        private int records;

        private void add(BigDecimal value) {
            quantity = quantity.add(value);
            records++;
        }
    }
}
