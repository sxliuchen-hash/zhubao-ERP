package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.ProgressRow;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在制进度。按客户范围过滤，不计算金额。
 */
@Service
public class ProgressBoardService {

    static final String WAITING_SPLIT = "待拆分";
    static final String WAITING_START = "待开工";
    static final String IN_PROCESS = "在制";
    static final String WAITING_QUALITY = "待质检";
    static final String QUALITY_DONE = "已质检";
    static final String REWORK = "返工";
    static final String NO_QUALITY = "未质检";

    private final BusinessDocumentRepository documents;

    public ProgressBoardService(BusinessDocumentRepository documents) {
        this.documents = documents;
    }

    public List<ProgressRow> list(
            OperatorContext operator,
            String customer,
            String process,
            String dueDate,
            String status
    ) {
        Map<String, List<ChildWorkOrderRecord>> children = new LinkedHashMap<>();
        for (ChildWorkOrderRecord child : documents.listAllChildWorkOrders()) {
            children.computeIfAbsent(child.motherOrderCode(), key -> new ArrayList<>()).add(child);
        }
        Map<String, Set<String>> submitted = new LinkedHashMap<>();
        for (ProcessStationHandoff handoff : documents.listAllProcessStationHandoffs()) {
            if (handoff.orderCode().startsWith("FG-")) {
                continue;
            }
            submitted.computeIfAbsent(handoff.orderCode(), key -> new LinkedHashSet<>()).add(handoff.processName());
        }
        Map<String, QualityRequirementCheck> checks = new LinkedHashMap<>();
        for (QualityRequirementCheck check : documents.listAllQualityRequirementChecks()) {
            checks.put(check.orderCode(), check);
        }

        List<ProgressRow> rows = new ArrayList<>();
        for (MotherOrderRecord mother : documents.listMotherOrders()) {
            if (!withinScope(operator, mother.customerId(), mother.customerName())) {
                continue;
            }
            List<ChildWorkOrderRecord> family = children.getOrDefault(mother.code(), List.of());
            MotherSplitState split = documents.findMotherSplitState(mother.code()).orElse(null);
            boolean splitDecision = split != null && MotherSplitState.SPLIT.equals(split.decision());
            if (family.isEmpty() && !splitDecision) {
                if (split != null && MotherSplitState.KEEP.equals(split.decision())) {
                    rows.add(stationRow(mother, mother.code(), mother.dueDate(), WorkshopProcesses.DEFAULT_ORDER, submitted, checks));
                } else {
                    rows.add(waitingSplit(mother));
                }
                continue;
            }
            for (ChildWorkOrderRecord child : family) {
                if (!withinScope(operator, child.customerId() == null ? mother.customerId() : child.customerId(), child.customerName())) {
                    continue;
                }
                rows.add(stationRow(
                        mother,
                        child.code(),
                        blank(child.dueDate()).isEmpty() ? mother.dueDate() : child.dueDate(),
                        WorkshopProcesses.split(child.processes()),
                        submitted,
                        checks
                ));
            }
        }
        String customerFilter = blank(customer);
        String processFilter = blank(process);
        String dueFilter = blank(dueDate);
        String statusFilter = blank(status);
        return rows.stream()
                .filter(row -> customerFilter.isEmpty()
                        || customerFilter.equals(row.customerId() == null ? "" : String.valueOf(row.customerId()))
                        || row.customerName().contains(customerFilter))
                .filter(row -> processFilter.isEmpty() || processFilter.equals(row.currentProcess()))
                .filter(row -> dueFilter.isEmpty() || row.dueDate().startsWith(dueFilter))
                .filter(row -> statusFilter.isEmpty() || statusFilter.equals(row.status()))
                .sorted(Comparator.comparing(ProgressRow::dueDate).thenComparing(ProgressRow::motherOrderCode).thenComparing(ProgressRow::workOrderCode))
                .toList();
    }

    private ProgressRow waitingSplit(MotherOrderRecord mother) {
        return new ProgressRow(
                mother.customerId(),
                blank(mother.customerName()),
                mother.code(),
                mother.code(),
                "来料",
                blank(mother.dueDate()),
                overdue(mother.dueDate(), WAITING_SPLIT, NO_QUALITY),
                NO_QUALITY,
                WAITING_SPLIT,
                "inbound"
        );
    }

    private ProgressRow stationRow(
            MotherOrderRecord mother,
            String orderCode,
            String dueDate,
            List<String> processes,
            Map<String, Set<String>> submitted,
            Map<String, QualityRequirementCheck> checks
    ) {
        List<String> steps = processes.isEmpty() ? WorkshopProcesses.DEFAULT_ORDER : processes;
        Set<String> done = submitted.getOrDefault(orderCode, Set.of());
        String current = steps.get(steps.size() - 1);
        String status = WAITING_QUALITY;
        boolean allDone = true;
        for (String step : steps) {
            if (!done.contains(step)) {
                current = step;
                status = done.isEmpty() ? WAITING_START : IN_PROCESS;
                allDone = false;
                break;
            }
        }
        QualityRequirementCheck check = checks.get(orderCode);
        String outcome = check == null ? NO_QUALITY : blank(check.outcome());
        if (check != null) {
            status = !check.reworkOrderCode().isBlank() ? REWORK : QUALITY_DONE;
        } else if (allDone) {
            status = WAITING_QUALITY;
        }
        String tab = switch (status) {
            case WAITING_START, IN_PROCESS -> "production";
            case WAITING_QUALITY, QUALITY_DONE, REWORK -> "quality";
            default -> "inbound";
        };
        Long customerId = mother.customerId();
        String customerName = blank(mother.customerName());
        return new ProgressRow(
                customerId,
                customerName,
                mother.code(),
                orderCode,
                current,
                blank(dueDate),
                overdue(dueDate, status, outcome),
                outcome.isEmpty() ? NO_QUALITY : outcome,
                status,
                tab
        );
    }

    private boolean withinScope(OperatorContext operator, Long customerId, String customerName) {
        if (operator == null || operator.dataScope() == DataScope.ALL || operator.customerScope().isEmpty()) {
            return true;
        }
        String idText = customerId == null ? "" : String.valueOf(customerId);
        String name = blank(customerName);
        return operator.customerScope().stream()
                .anyMatch(item -> item.equals(name) || (!idText.isEmpty() && item.equals(idText)));
    }

    private boolean overdue(String dueDate, String status, String outcome) {
        if (QUALITY_DONE.equals(status) && (QualityVerdicts.PASS.equals(outcome) || QualityVerdicts.CONCESSION.equals(outcome))) {
            return false;
        }
        String text = blank(dueDate);
        if (text.length() < 10) {
            return false;
        }
        try {
            return LocalDate.parse(text.substring(0, 10)).isBefore(LocalDate.now());
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
