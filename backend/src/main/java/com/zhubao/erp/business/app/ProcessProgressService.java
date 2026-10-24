package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ProcessProgressEvent;
import com.zhubao.erp.business.domain.ProcessProgressHead;
import com.zhubao.erp.business.domain.ProcessProgressRepository;
import com.zhubao.erp.business.domain.ProcessProgressRow;
import com.zhubao.erp.business.domain.ProcessProgressTarget;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 工序百分比。100% 只记录进度，不交付工序、不质检、不写库存或工资。
 */
@Service
public class ProcessProgressService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Set<Integer> PERCENTS = Set.of(0, 30, 60, 90, 100);
    private static final String PENDING = "待正式交付";
    private static final String DELIVERED = "已正式交付";

    private final ProcessStationService stations;
    private final ProcessProgressRepository progress;
    private final com.zhubao.erp.business.domain.BusinessDocumentRepository documents;
    private final BusinessDataAccess dataAccess;
    private final BusinessWorkUnit workUnit;

    public ProcessProgressService(
            ProcessStationService stations,
            ProcessProgressRepository progress,
            com.zhubao.erp.business.domain.BusinessDocumentRepository documents,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit
    ) {
        this.stations = stations;
        this.progress = progress;
        this.documents = documents;
        this.dataAccess = dataAccess;
        this.workUnit = workUnit;
    }

    public List<ProcessProgressRow> list(AuthUserAccount account, String query) {
        return list(account, query, null, null, null, null);
    }

    /**
     * 按客户、单号、工序和期限筛选后分页。页码从 1 开始；不传页码时返回全部匹配行。
     */
    public List<ProcessProgressRow> list(
            AuthUserAccount account,
            String query,
            String process,
            String dueBefore,
            Integer pageIndex,
            Integer pageSize
    ) {
        requireAccount(account);
        String needle = query == null ? "" : query.trim();
        List<ProcessProgressRow> rows = new ArrayList<>();
        for (ChildWorkOrderRecord child : documents.listAllChildWorkOrders()) {
            if (!visible(account, child.motherOrderCode(), child.code())) {
                continue;
            }
            rows.add(rowOf(child.code(), child.motherOrderCode(), child.customerName(), child.processes(), child.dueDate()));
        }
        for (MotherOrderRecord mother : documents.listMotherOrders()) {
            if (mother.quantity() == null || mother.quantity().signum() <= 0) continue;
            if (!documents.listChildWorkOrders(mother.code()).isEmpty()) {
                continue;
            }
            MotherSplitState state = documents.findMotherSplitState(mother.code()).orElse(null);
            if (state != null && MotherSplitState.SPLIT.equals(state.decision())) {
                continue;
            }
            if (!visible(account, mother.code(), mother.code())) {
                continue;
            }
            rows.add(rowOf(mother.code(), mother.code(), mother.customerName(), WorkshopProcesses.join(WorkshopProcesses.DEFAULT_ORDER), mother.dueDate()));
        }
        return page(rows.stream().filter(row -> matches(row, needle, process, dueBefore)).toList(), pageIndex, pageSize);
    }

    private boolean matches(ProcessProgressRow row, String needle, String process, String dueBefore) {
        if (process != null && !process.isBlank() && !process.trim().equals(row.currentProcess())) {
            return false;
        }
        if (dueBefore != null && !dueBefore.isBlank() && row.dueDate() != null && row.dueDate().compareTo(dueBefore.trim()) > 0) {
            return false;
        }
        if (needle.isEmpty()) {
            return true;
        }
        return contains(row.orderCode(), needle)
                || contains(row.motherOrderCode(), needle)
                || contains(row.customerName(), needle)
                || contains(row.currentProcess(), needle)
                || contains(String.valueOf(row.percent()), needle)
                || contains(row.dueDate(), needle);
    }

    private List<ProcessProgressRow> page(List<ProcessProgressRow> rows, Integer pageIndex, Integer pageSize) {
        if (pageIndex == null || pageIndex < 1) {
            return List.copyOf(rows);
        }
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((pageIndex - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return List.copyOf(rows.subList(from, to));
    }

    public ProcessProgressTarget open(String code, AuthUserAccount account) {
        ProcessProgressTarget target = stations.openProgress(code, account);
        if (!target.selectChild() && !visible(account, target.motherOrderCode(), target.orderCode())) {
            throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
        if (target.selectChild()) {
            return target;
        }
        // 当前百分比和版本跟这张单走，页面不能从分页列表里猜。
        ProcessProgressHead head = progress.find(target.orderCode(), target.currentProcess()).orElse(null);
        return new ProcessProgressTarget(
                false,
                target.orderCode(),
                target.motherOrderCode(),
                target.customerName(),
                target.processingRequirement(),
                target.dueDate(),
                target.currentProcess(),
                target.formallyDelivered(),
                head == null ? 0 : head.percent(),
                head == null ? 0 : head.version(),
                List.of()
        );
    }

    public List<ProcessProgressEvent> history(String orderCode, String processName, AuthUserAccount account) {
        requireAccount(account);
        if (!visible(account, "", orderCode) && documents.findChildWorkOrderByCode(orderCode).isEmpty()
                && documents.findMotherOrderByCode(orderCode).isEmpty()) {
            throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
        String mother = documents.findChildWorkOrderByCode(orderCode).map(ChildWorkOrderRecord::motherOrderCode).orElse(orderCode);
        if (!visible(account, mother, orderCode)) {
            throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
        return progress.history(orderCode, processName);
    }

    public ProcessProgressHead report(
            String code,
            int percent,
            int expectedVersion,
            String note,
            String reason,
            String requestId,
            AuthUserAccount account
    ) {
        requireAccount(account);
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException("进度报工必须带请求编号");
        }
        if (workUnit == null) {
            throw new BusinessException("进度回放尚未装配");
        }
        if (!PERCENTS.contains(percent)) {
            throw new BusinessException("进度只能是 0%、30%、60%、90% 或 100%");
        }
        ProcessProgressTarget target = stations.openProgress(code, account);
        if (target.selectChild()) {
            throw new BusinessException("这张母单已经拆成子单，请选择具体子单");
        }
        if (target.formallyDelivered()) {
            throw new BusinessException("当前工序已经正式交付，进度不能代替交付");
        }
        String noteText = trim(note, 200);
        String reasonText = trim(reason, 200);
        String actor = account.operator().trim();
        String payload = "process=" + target.currentProcess()
                + ";percent=" + percent
                + ";version=" + expectedVersion
                + ";note=" + noteText
                + ";reason=" + reasonText;
        workUnit.execute(new CommandCall(
                requestId,
                actor,
                "PROCESS_PROGRESS",
                "WORK_ORDER",
                target.orderCode(),
                payload
        ), session -> {
            ProcessProgressHead current = progress.find(target.orderCode(), target.currentProcess(), session).orElse(null);
            int version = current == null ? 0 : current.version();
            int oldPercent = current == null ? 0 : current.percent();
            if (version != expectedVersion) {
                throw new BusinessException("进度已被其他人更新，请刷新后重试");
            }
            if (percent < oldPercent && reasonText.isBlank()) {
                throw new BusinessException("回退进度必须填写原因");
            }
            String now = TIME.format(LocalDateTime.now());
            progress.append(new ProcessProgressEvent(
                    0, target.orderCode(), target.currentProcess(), oldPercent, percent, noteText, reasonText, actor, now
            ), session);
            progress.save(new ProcessProgressHead(
                    target.orderCode(), target.currentProcess(), percent, version + 1, noteText, actor, now
            ), session);
            return "progress";
        });
        return progress.find(target.orderCode(), target.currentProcess()).orElseThrow();
    }

    private ProcessProgressRow rowOf(String orderCode, String motherOrderCode, String customerName, String processes, String dueDate) {
        List<String> steps = WorkshopProcesses.split(processes);
        if (steps.isEmpty()) {
            steps = WorkshopProcesses.DEFAULT_ORDER;
        }
        String current = steps.get(steps.size() - 1);
        boolean delivered = true;
        for (String step : steps) {
            if (documents.findProcessStationHandoff(orderCode, step).isEmpty()) {
                current = step;
                delivered = false;
                break;
            }
        }
        ProcessProgressHead head = progress.find(orderCode, current).orElse(null);
        return new ProcessProgressRow(
                orderCode,
                motherOrderCode,
                customerName == null ? "" : customerName,
                current,
                delivered ? DELIVERED : PENDING,
                head == null ? 0 : head.percent(),
                head == null ? 0 : head.version(),
                head == null ? "" : head.operator(),
                head == null ? "" : head.updatedAt(),
                dueDate == null ? "" : dueDate
        );
    }

    private boolean visible(AuthUserAccount account, String motherOrderCode, String orderCode) {
        if (dataAccess == null) {
            return true;
        }
        String code = orderCode == null || orderCode.isBlank() ? motherOrderCode : orderCode;
        return dataAccess.canRead(BusinessDataAccess.contextOf(account), code);
    }

    private static void requireAccount(AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException("请先登录后再报进度");
        }
    }

    private static String trim(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            throw new BusinessException("说明超过可保存长度");
        }
        return text;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.contains(needle);
    }
}
