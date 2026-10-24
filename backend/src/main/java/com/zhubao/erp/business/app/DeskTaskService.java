package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.CancelDeskTaskCommand;
import com.zhubao.erp.business.domain.CompleteDeskTaskCommand;
import com.zhubao.erp.business.domain.CreateDeskTaskCommand;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.DeskAssignee;
import com.zhubao.erp.business.domain.DeskTask;
import com.zhubao.erp.business.domain.DeskTaskKind;
import com.zhubao.erp.business.domain.DeskTaskRepository;
import com.zhubao.erp.business.domain.DeskTaskStatus;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderCodes;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 工作台轻任务。只保存指派、完成和作废，不写工序、库存、质检或发货。
 */
@Service
public class DeskTaskService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Comparator<DeskTask> ORDER = Comparator
            .comparingInt((DeskTask task) -> task.status() == DeskTaskStatus.OPEN ? 0 : 1)
            .thenComparing(DeskTask::dueDate)
            .thenComparing(DeskTask::taskCode);

    private final DeskTaskRepository tasks;
    private final AuthUserAccountRepository accounts;
    private final MasterDataRepository customers;
    private final BusinessDocumentRepository documents;
    private final BusinessDataAccess access;
    private final PermissionGuard permissions;

    public DeskTaskService(
            DeskTaskRepository tasks,
            AuthUserAccountRepository accounts,
            MasterDataRepository customers,
            BusinessDocumentRepository documents,
            BusinessDataAccess access,
            PermissionGuard permissions
    ) {
        this.tasks = tasks;
        this.accounts = accounts;
        this.customers = customers;
        this.documents = documents;
        this.access = access;
        this.permissions = permissions;
    }

    public DeskTask create(CreateDeskTaskCommand command, AuthUserAccount actor) {
        if (command == null) {
            throw new BusinessException("请填写待办");
        }
        permissions.assertAllowed(PermissionCode.DESK_TASK_ASSIGN, context(actor));
        String requestId = required(command.requestId(), "请求编号", 80);
        AuthoredDeskTask draft = draft(command, actor);
        DeskTask existing = tasks.findByRequestId(requestId).orElse(null);
        if (existing != null) {
            return sameCreate(existing, draft, actor);
        }
        String createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        DeskTask inserted = null;
        for (int attempt = 0; attempt < 3 && inserted == null; attempt++) {
            String code = OrderCodes.next("DT", LocalDate.now(ZONE),
                    tasks.listAll().stream().map(DeskTask::taskCode).toList(),
                    candidate -> tasks.findByCode(candidate).isPresent());
            DeskTask task = new DeskTask(code, draft.kind(), draft.title(), draft.note(), draft.customerId(),
                    draft.customerName(), draft.motherOrderCode(), draft.assigneeUsername(), draft.assigneeName(),
                    draft.dueDate(), DeskTaskStatus.OPEN, actor.username().trim(), createdAt, requestId, 1,
                    "", "", "", "", "", "", "", "");
            try {
                inserted = tasks.insert(task);
            } catch (BusinessException ex) {
                if (ex.getMessage().contains("请求编号已使用")) {
                    DeskTask saved = tasks.findByRequestId(requestId).orElseThrow(() -> ex);
                    return sameCreate(saved, draft, actor);
                }
                if (!ex.getMessage().contains("待办单号已存在")) {
                    throw ex;
                }
            }
        }
        if (inserted == null) {
            throw new BusinessException("当天单号已用完，请稍后重试");
        }
        return inserted;
    }

    public List<DeskTask> list(String view, AuthUserAccount actor) {
        requireActor(actor);
        String mode = text(view).toLowerCase();
        if (mode.isEmpty() || "assigned".equals(mode)) {
            return tasks.listAll().stream()
                    .filter(task -> task.status() == DeskTaskStatus.OPEN)
                    .filter(task -> sameUser(task.assigneeUsername(), actor.username()))
                    .sorted(ORDER)
                    .toList();
        }
        if ("issued".equals(mode)) {
            return tasks.listAll().stream()
                    .filter(task -> sameUser(task.creatorUsername(), actor.username()))
                    .sorted(ORDER)
                    .toList();
        }
        if ("open".equals(mode)) {
            if (actor.role() != RoleCode.ADMIN) {
                throw new PermissionDeniedException("只有管理员可以查看全部未完成待办");
            }
            return tasks.listAll().stream()
                    .filter(task -> task.status() == DeskTaskStatus.OPEN)
                    .sorted(ORDER)
                    .toList();
        }
        throw new BusinessException("未知待办视图");
    }

    public List<DeskAssignee> assignees(AuthUserAccount actor) {
        permissions.assertAllowed(PermissionCode.DESK_TASK_ASSIGN, context(actor));
        return accounts.findAll().stream()
                .filter(AuthUserAccount::enabled)
                .map(account -> new DeskAssignee(account.username(), account.operator()))
                .sorted(Comparator.comparing(DeskAssignee::operator).thenComparing(DeskAssignee::username))
                .toList();
    }

    public DeskTask complete(String taskCode, CompleteDeskTaskCommand command, AuthUserAccount actor) {
        requireActor(actor);
        if (command == null) {
            throw new BusinessException("请填写完成信息");
        }
        String requestId = required(command.requestId(), "请求编号", 80);
        String note = limited(command.resultNote(), "结果说明", 500);
        DeskTask replay = tasks.findByCompleteRequestId(requestId).orElse(null);
        if (replay != null) {
            return sameCompletion(replay, taskCode, note, actor);
        }
        DeskTask current = requireTask(taskCode);
        if (current.status() == DeskTaskStatus.DONE) {
            throw new BusinessException("待办已经完成");
        }
        if (current.status() == DeskTaskStatus.CANCELLED) {
            throw new BusinessException("待办已经作废");
        }
        if (!sameUser(current.assigneeUsername(), actor.username()) && actor.role() != RoleCode.ADMIN) {
            throw new PermissionDeniedException("只能由责任人完成这项待办");
        }
        if (current.version() != command.expectedVersion()) {
            throw new BusinessException("请刷新后重试");
        }
        DeskTask next = new DeskTask(current.taskCode(), current.kind(), current.title(), current.note(),
                current.customerId(), current.customerName(), current.motherOrderCode(), current.assigneeUsername(),
                current.assigneeName(), current.dueDate(), DeskTaskStatus.DONE, current.creatorUsername(),
                current.createdAt(), current.requestId(), current.version() + 1,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), actor.username().trim(), note, requestId,
                "", "", "", "");
        return write(next, current.version(), requestId, true);
    }

    public DeskTask cancel(String taskCode, CancelDeskTaskCommand command, AuthUserAccount actor) {
        requireActor(actor);
        if (command == null) {
            throw new BusinessException("请填写作废信息");
        }
        String requestId = required(command.requestId(), "请求编号", 80);
        String reason = required(command.reason(), "作废原因", 500);
        DeskTask replay = tasks.findByCancelRequestId(requestId).orElse(null);
        if (replay != null) {
            return sameCancellation(replay, taskCode, reason, actor);
        }
        DeskTask current = requireTask(taskCode);
        if (current.status() == DeskTaskStatus.CANCELLED) {
            throw new BusinessException("待办已经作废");
        }
        if (current.status() == DeskTaskStatus.DONE) {
            throw new BusinessException("待办已经完成，不能作废");
        }
        if (!sameUser(current.creatorUsername(), actor.username()) && actor.role() != RoleCode.ADMIN) {
            throw new PermissionDeniedException("只能由创建人作废这项待办");
        }
        if (current.version() != command.expectedVersion()) {
            throw new BusinessException("请刷新后重试");
        }
        DeskTask next = new DeskTask(current.taskCode(), current.kind(), current.title(), current.note(),
                current.customerId(), current.customerName(), current.motherOrderCode(), current.assigneeUsername(),
                current.assigneeName(), current.dueDate(), DeskTaskStatus.CANCELLED, current.creatorUsername(),
                current.createdAt(), current.requestId(), current.version() + 1,
                "", "", "", "",
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), actor.username().trim(), reason, requestId);
        return write(next, current.version(), requestId, false);
    }

    private DeskTask write(DeskTask next, int expectedVersion, String requestId, boolean completion) {
        try {
            DeskTask saved = tasks.updateIfVersion(next, expectedVersion).orElse(null);
            if (saved != null) {
                return saved;
            }
        } catch (BusinessException ex) {
            DeskTask raced = completion
                    ? tasks.findByCompleteRequestId(requestId).orElse(null)
                    : tasks.findByCancelRequestId(requestId).orElse(null);
            if (raced != null) {
                return completion
                        ? sameCompletion(raced, next.taskCode(), next.resultNote(), actorOf(next.completedBy()))
                        : sameCancellation(raced, next.taskCode(), next.cancelReason(), actorOf(next.cancelledBy()));
            }
            throw ex;
        }
        DeskTask raced = completion
                ? tasks.findByCompleteRequestId(requestId).orElse(null)
                : tasks.findByCancelRequestId(requestId).orElse(null);
        if (raced != null) {
            return completion
                    ? sameCompletion(raced, next.taskCode(), next.resultNote(), actorOf(next.completedBy()))
                    : sameCancellation(raced, next.taskCode(), next.cancelReason(), actorOf(next.cancelledBy()));
        }
        throw new BusinessException("请刷新后重试");
    }

    private AuthUserAccount actorOf(String username) {
        return accounts.findByUsername(username).orElseThrow(() -> new BusinessException("请刷新后重试"));
    }

    private AuthoredDeskTask draft(CreateDeskTaskCommand command, AuthUserAccount actor) {
        DeskTaskKind kind = DeskTaskKind.parse(command.kind());
        String title = required(command.title(), "标题", 80);
        String note = limited(command.note(), "说明", 500);
        String dueDate = date(command.dueDate());
        AuthUserAccount assignee = assignee(command.assigneeUsername());
        Long customerId = null;
        String customerName = "";
        if (command.customerId() != null && command.customerId() > 0) {
            CustomerProfile profile = customers.findCustomer(command.customerId())
                    .orElseThrow(() -> new BusinessException("客户档案不存在"));
            if (!profile.enabled()) {
                throw new BusinessException("客户已停用，不能用于新待办");
            }
            if (profile.name().length() > 120) {
                throw new BusinessException("客户名称过长");
            }
            if (!CustomerScopeRules.allowed(context(actor), profile.customerId(), profile.name())) {
                throw new PermissionDeniedException("客户不在当前账号的授权范围内");
            }
            customerId = profile.customerId();
            customerName = profile.name();
        }
        String motherCode = text(command.motherOrderCode());
        if (!motherCode.isEmpty()) {
            if (motherCode.length() > 40) {
                throw new BusinessException("母单号过长");
            }
            MotherOrderRecord mother = visibleMother(motherCode, actor);
            if (customerId != null && !Objects.equals(customerId, mother.customerId())) {
                throw new BusinessException("客户与母单不一致");
            }
            if (customerId == null) {
                customerId = mother.customerId();
                customerName = text(mother.customerName());
            }
        }
        return new AuthoredDeskTask(kind, title, note, customerId, customerName, motherCode,
                assignee.username(), assignee.operator(), dueDate);
    }

    private MotherOrderRecord visibleMother(String code, AuthUserAccount actor) {
        MotherOrderRecord mother = documents.findMotherOrderByCode(code).orElse(null);
        if (mother == null) {
            if (actor.role() == RoleCode.ADMIN || actor.dataScope() == DataScope.ALL) {
                throw new BusinessException("母单不存在");
            }
            throw new PermissionDeniedException("母单不在当前账号的业务数据范围内");
        }
        if (!access.canRead(context(actor), mother.code())) {
            throw new PermissionDeniedException("母单不在当前账号的业务数据范围内");
        }
        return mother;
    }

    private AuthUserAccount assignee(String username) {
        String name = text(username);
        if (name.isEmpty()) {
            throw new BusinessException("请选择责任人");
        }
        if (name.length() > 64) {
            throw new BusinessException("责任人用户名过长");
        }
        AuthUserAccount account = accounts.findByUsername(name).orElseThrow(() -> new BusinessException("责任人不存在"));
        if (!account.enabled()) {
            throw new BusinessException("责任人已停用");
        }
        return account;
    }

    private DeskTask sameCreate(DeskTask saved, AuthoredDeskTask draft, AuthUserAccount actor) {
        boolean same = saved.kind() == draft.kind()
                && saved.title().equals(draft.title())
                && saved.note().equals(draft.note())
                && Objects.equals(saved.customerId(), draft.customerId())
                && saved.motherOrderCode().equals(draft.motherOrderCode())
                && sameUser(saved.assigneeUsername(), draft.assigneeUsername())
                && saved.dueDate().equals(draft.dueDate())
                && sameUser(saved.creatorUsername(), actor.username());
        if (!same) {
            throw new BusinessException("同一请求的待办内容不一致");
        }
        return saved;
    }

    private DeskTask sameCompletion(DeskTask saved, String taskCode, String note, AuthUserAccount actor) {
        if (saved.taskCode().equals(text(taskCode))
                && saved.resultNote().equals(note)
                && sameUser(saved.completedBy(), actor.username())) {
            return saved;
        }
        throw new BusinessException("同一请求的完成内容不一致");
    }

    private DeskTask sameCancellation(DeskTask saved, String taskCode, String reason, AuthUserAccount actor) {
        if (saved.taskCode().equals(text(taskCode))
                && saved.cancelReason().equals(reason)
                && sameUser(saved.cancelledBy(), actor.username())) {
            return saved;
        }
        throw new BusinessException("同一请求的作废内容不一致");
    }

    private DeskTask requireTask(String taskCode) {
        return tasks.findByCode(text(taskCode)).orElseThrow(() -> new BusinessException("待办不存在"));
    }

    private OperatorContext context(AuthUserAccount actor) {
        return BusinessDataAccess.contextOf(requireActor(actor));
    }

    private AuthUserAccount requireActor(AuthUserAccount actor) {
        if (actor == null || text(actor.username()).isEmpty()) {
            throw new PermissionDeniedException("请先登录后再操作业务");
        }
        return actor;
    }

    private String date(String raw) {
        String value = required(raw, "期限", 10);
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException ex) {
            throw new BusinessException("期限格式应为年-月-日");
        }
    }

    private String required(String value, String label, int max) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(label + "不能为空");
        }
        if (text.length() > max) {
            throw new BusinessException(label + "过长");
        }
        return text;
    }

    private String limited(String value, String label, int max) {
        String text = text(value);
        if (text.length() > max) {
            throw new BusinessException(label + "过长");
        }
        return text;
    }

    private static boolean sameUser(String left, String right) {
        return text(left).equalsIgnoreCase(text(right));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** 已核对过客户、母单和责任人的新建内容。名称是保存当时的快照。 */
    private record AuthoredDeskTask(
            DeskTaskKind kind,
            String title,
            String note,
            Long customerId,
            String customerName,
            String motherOrderCode,
            String assigneeUsername,
            String assigneeName,
            String dueDate
    ) {
    }
}
