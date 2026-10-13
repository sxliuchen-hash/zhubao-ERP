package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.business.domain.OrderChangeStatus;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.app.OrderChangePostingService;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 改单只追加版本。生效前必须有财务确认，确认可以明确选择金额不变。
 */
@Service
public class OrderChangeService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrderChangeRepository changes;
    private final BusinessDocumentRepository documents;
    private final BusinessWorkUnit workUnit;
    private final OrderChangePostingService posting;

    public OrderChangeService(OrderChangeRepository changes, BusinessDocumentRepository documents, BusinessWorkUnit workUnit) {
        this(changes, documents, workUnit, null);
    }

    @Autowired
    public OrderChangeService(
            OrderChangeRepository changes,
            BusinessDocumentRepository documents,
            BusinessWorkUnit workUnit,
            OrderChangePostingService posting
    ) {
        this.changes = changes;
        this.documents = documents;
        this.workUnit = workUnit;
        this.posting = posting;
    }

    public OrderChange draft(String motherOrderCode, String workOrderCode, String newRequirement, String newDueDate, String requestId, AuthUserAccount account) {
        requireAccount(account);
        if (documents.findMotherOrderByCode(motherOrderCode).isEmpty()) {
            throw new BusinessException("母单不存在：" + motherOrderCode);
        }
        String oldRequirement = documents.findMotherOrderByCode(motherOrderCode).orElseThrow().processingRequirement();
        String oldDue = documents.findMotherOrderByCode(motherOrderCode).orElseThrow().dueDate();
        OrderChange existing = changes.findByRequestId(requestId).orElse(null);
        if (existing != null) {
            return existing;
        }
        String now = TIME.format(LocalDateTime.now());
        int version = changes.listByMother(motherOrderCode).size() + 1;
        return changes.save(new OrderChange(
                "CHG-" + UUID.randomUUID().toString().substring(0, 8),
                motherOrderCode,
                workOrderCode == null ? "" : workOrderCode,
                oldRequirement == null ? "" : oldRequirement,
                newRequirement == null ? "" : newRequirement.trim(),
                oldDue == null ? "" : oldDue,
                newDueDate == null ? "" : newDueDate.trim(),
                OrderChangeStatus.DRAFT,
                version,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "",
                "",
                requestId,
                account.operator().trim(),
                now,
                now,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "",
                "",
                ""
        ));
    }

    public OrderChange submitFinance(String id, AuthUserAccount account) {
        OrderChange current = require(id);
        if (current.status() != OrderChangeStatus.DRAFT) {
            throw new BusinessException("只有草稿可以送财务确认");
        }
        return saveStatus(current, OrderChangeStatus.PENDING_FINANCE, current.amountUnchanged(), current.customerUnitPrice(), current.workerFee(), current.financeNote(), current.confirmedBy());
    }

    public OrderChange confirmFinance(String id, boolean amountUnchanged, BigDecimal customerUnitPrice, BigDecimal workerFee, String note, AuthUserAccount account) {
        return confirmFinance(id, amountUnchanged, customerUnitPrice, workerFee, BigDecimal.ZERO, BigDecimal.ZERO, "", "", "", note, account);
    }

    public OrderChange confirmFinance(
            String id,
            boolean amountUnchanged,
            BigDecimal customerUnitPrice,
            BigDecimal workerFee,
            BigDecimal customerDelta,
            BigDecimal workerDelta,
            String receivableCode,
            String processName,
            String workerName,
            String note,
            AuthUserAccount account
    ) {
        requireFinance(account);
        OrderChange current = require(id);
        if (current.status() != OrderChangeStatus.PENDING_FINANCE) {
            throw new BusinessException("改单不在待财务确认状态");
        }
        if (!amountUnchanged && (customerUnitPrice == null || workerFee == null)) {
            throw new BusinessException("财务必须确认客户单价和工人工费，或明确选择金额不变");
        }
        BigDecimal customer = customerDelta == null ? BigDecimal.ZERO : customerDelta;
        BigDecimal worker = workerDelta == null ? BigDecimal.ZERO : workerDelta;
        if (amountUnchanged && (customer.signum() != 0 || worker.signum() != 0)) {
            throw new BusinessException("金额不变时不能再填写差额");
        }
        if (customer.signum() < 0 && (receivableCode == null || receivableCode.isBlank())) {
            throw new BusinessException("减额必须指定要冲销的应收行");
        }
        if (customer.signum() > 0 && (processName == null || processName.isBlank())) {
            throw new BusinessException("增额必须指定工序");
        }
        if (worker.signum() != 0 && (workerName == null || workerName.isBlank() || processName == null || processName.isBlank())) {
            throw new BusinessException("工费差额必须指定工人和工序");
        }
        return changes.save(new OrderChange(
                current.id(), current.motherOrderCode(), current.workOrderCode(), current.oldRequirement(), current.newRequirement(),
                current.oldDueDate(), current.newDueDate(), OrderChangeStatus.FINANCE_CONFIRMED, current.versionNo(), amountUnchanged,
                amountUnchanged ? BigDecimal.ZERO : customerUnitPrice,
                amountUnchanged ? BigDecimal.ZERO : workerFee,
                note == null ? "" : note.trim(),
                account.operator().trim(),
                current.requestId(), current.createdBy(), current.createdAt(), TIME.format(LocalDateTime.now()),
                customer, worker,
                receivableCode == null ? "" : receivableCode.trim(),
                processName == null ? "" : processName.trim(),
                workerName == null ? "" : workerName.trim()
        ));
    }

    public OrderChange rejectFinance(String id, String note, AuthUserAccount account) {
        requireFinance(account);
        OrderChange current = require(id);
        if (current.status() != OrderChangeStatus.PENDING_FINANCE) {
            throw new BusinessException("改单不在待财务确认状态");
        }
        return saveStatus(current, OrderChangeStatus.REJECTED, false, BigDecimal.ZERO, BigDecimal.ZERO, note == null ? "" : note.trim(), account.operator().trim());
    }

    public OrderChange apply(String id, String requestId, AuthUserAccount account) {
        requireAccount(account);
        if (workUnit == null) {
            throw new BusinessException("改单回放尚未装配");
        }
        OrderChange current = require(id);
        if (current.status() != OrderChangeStatus.FINANCE_CONFIRMED && current.status() != OrderChangeStatus.APPLIED) {
            throw new BusinessException("财务确认后才能生效");
        }
        List<ProcessStationHandoff> before = documents.listProcessStationHandoffs(
                current.workOrderCode().isBlank() ? current.motherOrderCode() : current.workOrderCode());
        workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "ORDER_CHANGE_APPLY",
                "MOTHER_ORDER",
                current.motherOrderCode(),
                "change=" + current.id() + ";version=" + current.versionNo()
        ), session -> {
            OrderChange latest = require(id);
            if (latest.status() == OrderChangeStatus.APPLIED) {
                return "applied";
            }
            if (latest.status() != OrderChangeStatus.FINANCE_CONFIRMED) {
                throw new BusinessException("财务确认后才能生效");
            }
            if (posting != null) {
                posting.post(latest, account.operator().trim());
            }
            changes.save(copy(latest, OrderChangeStatus.APPLIED, latest.amountUnchanged(), latest.customerUnitPrice(), latest.workerFee(), latest.financeNote(), latest.confirmedBy()));
            return "applied";
        });
        List<ProcessStationHandoff> after = documents.listProcessStationHandoffs(
                current.workOrderCode().isBlank() ? current.motherOrderCode() : current.workOrderCode());
        if (before.size() != after.size()) {
            throw new IllegalStateException("改单生效不得改写已有交付");
        }
        return require(id);
    }

    public List<String> timeline(String motherOrderCode, AuthUserAccount account) {
        requireAccount(account);
        List<String> lines = new ArrayList<>();
        documents.findMotherOrderByCode(motherOrderCode).ifPresent(mother ->
                lines.add("收货 " + mother.code() + " " + mother.createdAt()));
        documents.listChildWorkOrders(motherOrderCode).forEach(child ->
                lines.add("子单 " + child.code() + " " + child.createdAt()));
        for (var child : documents.listChildWorkOrders(motherOrderCode)) {
            documents.listProcessStationHandoffs(child.code()).forEach(handoff ->
                    lines.add("交付 " + handoff.orderCode() + " " + handoff.processName() + " " + handoff.createdAt()));
        }
        documents.listProcessStationHandoffs(motherOrderCode).forEach(handoff ->
                lines.add("交付 " + handoff.orderCode() + " " + handoff.processName() + " " + handoff.createdAt()));
        changes.listByMother(motherOrderCode).forEach(change ->
                lines.add("改单 " + change.id() + " V" + change.versionNo() + " " + change.status() + " 原要求:" + change.oldRequirement() + " 新要求:" + change.newRequirement()));
        return List.copyOf(lines);
    }

    private OrderChange saveStatus(OrderChange current, OrderChangeStatus status, boolean unchanged, BigDecimal price, BigDecimal fee, String note, String confirmedBy) {
        return changes.save(copy(current, status, unchanged, price, fee, note, confirmedBy));
    }

    private OrderChange copy(OrderChange current, OrderChangeStatus status, boolean unchanged, BigDecimal price, BigDecimal fee, String note, String confirmedBy) {
        return new OrderChange(
                current.id(), current.motherOrderCode(), current.workOrderCode(), current.oldRequirement(), current.newRequirement(),
                current.oldDueDate(), current.newDueDate(), status, current.versionNo(), unchanged, price, fee, note, confirmedBy,
                current.requestId(), current.createdBy(), current.createdAt(), TIME.format(LocalDateTime.now()),
                current.customerDelta(), current.workerDelta(), current.receivableCode(), current.processName(), current.workerName()
        );
    }

    private OrderChange require(String id) {
        return changes.findById(id).orElseThrow(() -> new BusinessException("改单不存在：" + id));
    }

    private void requireAccount(AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException("请先登录后再改单");
        }
    }

    private void requireFinance(AuthUserAccount account) {
        requireAccount(account);
        if (account.role() != RoleCode.FINANCE_STATISTICIAN && account.role() != RoleCode.ADMIN && account.role() != RoleCode.GENERAL_MANAGER) {
            throw new PermissionDeniedException("只有财务或管理员可以确认改单金额");
        }
    }
}
