package com.zhubao.erp.machine.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.machine.domain.BindMachineOutputCommand;
import com.zhubao.erp.machine.domain.MachineOutputBindingResult;
import com.zhubao.erp.machine.domain.MachineOutputMachineMatchSuggestion;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    public List<MachineOutputMachineMatchSuggestion> machineMatchSuggestions(int limit) {
        Map<String, MatchAccumulator> suggestions = new LinkedHashMap<>();
        for (MachineOutputRecord record : recent(limit)) {
            suggestions.computeIfAbsent(
                    normalizedMachineNo(record),
                    ignored -> new MatchAccumulator(blankToDefault(record.machineNoSnapshot(), "UNKNOWN"))
            ).add(record);
        }
        return suggestions.values().stream()
                .filter(MatchAccumulator::hasUnmatchedRecords)
                .map(MatchAccumulator::toSuggestion)
                .sorted(Comparator
                        .comparing(MachineOutputMachineMatchSuggestion::unmatchedRecords).reversed()
                        .thenComparing(MachineOutputMachineMatchSuggestion::machineNoSnapshot))
                .toList();
    }

    public List<MachineOutputRecord> byImportBatchNo(String importBatchNo) {
        if (importBatchNo == null || importBatchNo.isBlank()) {
            throw new BusinessException("缺少导入批次号");
        }
        return repository.findByImportBatchNo(importBatchNo.trim());
    }

    /**
     * 将现场机器编号的未绑定设备产量记录绑定到机器档案 ID。
     *
     * <p>承接 M3.121 的匹配建议：复核候选机器档案 ID 后调用本方法完成人工绑定，仅影响同编号且
     * 尚未绑定的记录，不覆盖历史绑定。</p>
     */
    public MachineOutputBindingResult bindMachine(BindMachineOutputCommand command) {
        if (command == null) {
            throw new BusinessException("设备产量绑定命令不能为空");
        }
        require(command.machineNoSnapshot(), "machineNoSnapshot");
        if (command.machineId() == null || command.machineId() <= 0) {
            throw new BusinessException("machineId 必须为正整数");
        }
        String machineNo = command.machineNoSnapshot().trim();
        int bound = repository.bindMachineId(machineNo, command.machineId());
        if (bound == 0) {
            throw new BusinessException("没有可绑定的记录：机器编号 " + machineNo + " 不存在未绑定记录");
        }
        return new MachineOutputBindingResult(
                machineNo,
                command.machineId(),
                bound,
                blankToDefault(command.operator(), "机器绑定复核员")
        );
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

    private String normalizedMachineNo(MachineOutputRecord record) {
        return blankToDefault(record.machineNoSnapshot(), "UNKNOWN").toUpperCase(Locale.ROOT);
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

    private static class MatchAccumulator {
        private final String machineNoSnapshot;
        private final Set<Long> candidateMachineIds = new LinkedHashSet<>();
        private int matchedRecords;
        private int unmatchedRecords;
        private int totalRecords;
        private String productCategory = "";
        private String latestWorkDate = "";
        private String sourceRegisterType = "";
        private String sourceMachineLineNo = "";

        private MatchAccumulator(String machineNoSnapshot) {
            this.machineNoSnapshot = machineNoSnapshot;
        }

        private void add(MachineOutputRecord record) {
            totalRecords++;
            if (record.machineId() == null) {
                unmatchedRecords++;
            } else {
                matchedRecords++;
                candidateMachineIds.add(record.machineId());
            }
            productCategory = firstNonBlank(productCategory, record.productCategory());
            sourceRegisterType = firstNonBlank(sourceRegisterType, record.sourceRegisterType());
            sourceMachineLineNo = firstNonBlank(sourceMachineLineNo, record.machineLineNo());
            if (record.workDate() != null && record.workDate().compareTo(latestWorkDate) > 0) {
                latestWorkDate = record.workDate();
            }
        }

        private boolean hasUnmatchedRecords() {
            return unmatchedRecords > 0;
        }

        private MachineOutputMachineMatchSuggestion toSuggestion() {
            String readiness;
            String action;
            if (candidateMachineIds.isEmpty()) {
                readiness = "NEEDS_MASTER_DATA";
                action = "Create machine master data or confirm the low-cost device number before binding records.";
            } else if (candidateMachineIds.size() == 1) {
                readiness = "READY_TO_BIND";
                action = "Bind unmatched records to candidate machine id " + candidateMachineIds.iterator().next() + ".";
            } else {
                readiness = "CONFLICT";
                action = "Review duplicate machine master mappings before binding unmatched records.";
            }
            return new MachineOutputMachineMatchSuggestion(
                    machineNoSnapshot,
                    productCategory,
                    List.copyOf(candidateMachineIds),
                    matchedRecords,
                    unmatchedRecords,
                    totalRecords,
                    latestWorkDate,
                    sourceRegisterType,
                    sourceMachineLineNo,
                    readiness,
                    action
            );
        }

        private String firstNonBlank(String current, String next) {
            if (current != null && !current.isBlank()) {
                return current;
            }
            return next == null ? "" : next.trim();
        }
    }
}
