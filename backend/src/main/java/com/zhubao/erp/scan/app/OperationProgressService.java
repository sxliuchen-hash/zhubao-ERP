package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.scan.domain.OperationProgressRecord;
import com.zhubao.erp.scan.domain.OperationProgressRepository;
import com.zhubao.erp.scan.domain.SaveOperationProgressCommand;
import com.zhubao.erp.scan.domain.ScanProgressRecord;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 追加生产、质检或发货准备说明。不写工序交付、质检结论、工资、库存或出货单。
 * 相同请求、同一操作者和同一规范化内容重试时返回原记录。这不是正式命令的幂等回放。
 */
@Service
public class OperationProgressService {

    static final String PRODUCTION = EvidenceCodes.PRODUCTION;
    static final String QUALITY = EvidenceCodes.QUALITY;
    static final String SHIPPING = EvidenceCodes.SHIPPING;
    private static final Set<String> STAGES = Set.of(PRODUCTION, QUALITY, SHIPPING);
    private static final DateTimeFormatter RECORDED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CODE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int NOTE_MAX = 500;
    private static final int PROCESS_MAX = 20;
    private static final int CLAUSE_MAX = 32;
    private static final int CODE_MAX = 90;

    private final OperationProgressRepository progress;
    private final BusinessDocumentRepository documents;
    private final BusinessDataAccess dataAccess;
    private final PermissionGuard permissions;
    private final AtomicInteger sequence = new AtomicInteger();

    public OperationProgressService(
            OperationProgressRepository progress,
            BusinessDocumentRepository documents,
            BusinessDataAccess dataAccess,
            PermissionGuard permissions
    ) {
        this.progress = progress;
        this.documents = documents;
        this.dataAccess = dataAccess;
        this.permissions = permissions;
    }

    public synchronized ScanProgressRecord save(SaveOperationProgressCommand command, AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()
                || account.username() == null || account.username().isBlank()) {
            throw new PermissionDeniedException("请先登录后再记录过程");
        }
        if (command == null) {
            throw new BusinessException("请填写过程说明");
        }
        String stage = normalizeStage(command.stage());
        String process = normalizeToken(command.process(), PROCESS_MAX, "工序");
        String clauseNo = normalizeToken(command.clauseNo(), CLAUSE_MAX, "条款号");
        String note = normalizeNote(command.note());
        String requestId = normalizeRequestId(command.requestId());
        if (note.isEmpty()) {
            throw new BusinessException("请填写过程说明");
        }
        if (note.length() > NOTE_MAX) {
            throw new BusinessException("说明超过可保存长度");
        }
        OperatorContext operator = BusinessDataAccess.contextOf(account);
        ResolvedTarget target = resolve(command, account, operator);
        permissions.assertAllowed(permissionFor(stage), operator);
        assertProcess(stage, process, target, account);
        String hash = contentHash(stage, process, clauseNo, note);
        ScanProgressRecord replay = replay(requestId, account, operator, target, hash);
        if (replay != null) {
            return replay;
        }
        boolean supplementary = supplementary(stage, process, target);
        OperationProgressRecord record = new OperationProgressRecord(
                nextCode(),
                target.targetType(),
                target.targetCode(),
                target.motherOrderCode(),
                stage,
                process,
                clauseNo,
                note,
                account.username().trim(),
                account.operator().trim(),
                account.role().name(),
                supplementary,
                RECORDED_AT.format(LocalDateTime.now()),
                requestId,
                hash
        );
        try {
            progress.insert(record);
        } catch (RuntimeException ex) {
            OperationProgressRecord again = progress.findReplay(
                    requestId, account.username().trim(), target.targetType(), target.targetCode(), hash
            ).orElse(null);
            if (again == null) {
                throw ex;
            }
            return again.toScan();
        }
        return record.toScan();
    }

    private ScanProgressRecord replay(
            String requestId,
            AuthUserAccount account,
            OperatorContext operator,
            ResolvedTarget target,
            String hash
    ) {
        String operatorId = account.username().trim();
        List<OperationProgressRecord> sameRequest = progress.findByRequestId(requestId);
        for (OperationProgressRecord existing : sameRequest) {
            if (operatorId.equals(existing.operatorId())
                    && target.targetType().equals(existing.targetType())
                    && target.targetCode().equals(existing.targetCode())
                    && hash.equals(existing.contentHash())) {
                return existing.toScan();
            }
        }
        for (OperationProgressRecord existing : sameRequest) {
            if (!dataAccess.canRead(operator, existing.targetCode())) {
                throw new PermissionDeniedException("该请求不能跨客户重放");
            }
            if (!operatorId.equals(existing.operatorId())) {
                throw new BusinessException("该请求不能跨账号重放");
            }
            if (!target.targetType().equals(existing.targetType()) || !target.targetCode().equals(existing.targetCode())) {
                throw new BusinessException("该请求不能跨单重放");
            }
        }
        return null;
    }

    private ResolvedTarget resolve(SaveOperationProgressCommand command, AuthUserAccount account, OperatorContext operator) {
        String type = text(command.targetType());
        String code = text(command.targetCode());
        String claimedMother = text(command.motherOrderCode());
        if (!EvidenceCodes.MOTHER_ORDER.equals(type) && !EvidenceCodes.WORK_ORDER.equals(type)) {
            throw new BusinessException("过程记录只能记在母单或子单上");
        }
        checkLength(code, CODE_MAX, "单据编号");
        checkLength(claimedMother, CODE_MAX, "母单号");
        if (account.dataScope() != DataScope.ALL && !dataAccess.canRead(operator, code)) {
            throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
        if (EvidenceCodes.MOTHER_ORDER.equals(type)) {
            MotherOrderRecord mother = documents.findMotherOrderByCode(code).orElse(null);
            if (mother == null) {
                throw missing(account);
            }
            if (!dataAccess.canRead(operator, mother.code())) {
                throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
            }
            if (splitMother(mother.code())) {
                throw new BusinessException("已拆母单必须记到选中的子单");
            }
            boolean keep = documents.findMotherSplitState(mother.code())
                    .map(state -> MotherSplitState.KEEP.equals(state.decision()))
                    .orElse(false);
            if (!keep) {
                throw new BusinessException("不拆母单才能把过程记在母单上");
            }
            if (!claimedMother.isEmpty() && !claimedMother.equals(mother.code())) {
                throw new BusinessException("不能跨母子单记录");
            }
            return new ResolvedTarget(type, mother.code(), mother.code(), WorkshopProcesses.DEFAULT_ORDER);
        }
        ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(code).orElse(null);
        if (child == null) {
            throw missing(account);
        }
        if (!dataAccess.canRead(operator, child.code())) {
            throw new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
        if (!claimedMother.isEmpty() && !claimedMother.equals(child.motherOrderCode())) {
            throw new BusinessException("不能跨母子单记录");
        }
        List<String> processes = WorkshopProcesses.split(child.processes());
        if (processes.isEmpty()) {
            processes = WorkshopProcesses.DEFAULT_ORDER;
        }
        return new ResolvedTarget(EvidenceCodes.WORK_ORDER, child.code(), child.motherOrderCode(), processes);
    }

    private void assertProcess(String stage, String process, ResolvedTarget target, AuthUserAccount account) {
        if (!PRODUCTION.equals(stage)) {
            return;
        }
        if (!process.isEmpty() && !target.processes().contains(process)) {
            throw new BusinessException("这张单没有「" + process + "」这道工序");
        }
        List<String> bound = WorkshopProcesses.bind(account.allowedProcesses());
        if (!bound.isEmpty()) {
            if (process.isEmpty()) {
                throw new BusinessException("请选择工序");
            }
            if (!bound.contains(process)) {
                throw new BusinessException("当前账号不能做这道工序");
            }
            return;
        }
        if (account.role() == RoleCode.OPERATOR) {
            throw new BusinessException("当前账号未绑定工序");
        }
    }

    private boolean supplementary(String stage, String process, ResolvedTarget target) {
        if (PRODUCTION.equals(stage)) {
            if (!process.isEmpty()) {
                return documents.findProcessStationHandoff(target.targetCode(), process).isPresent();
            }
            return target.processes().stream()
                    .allMatch(name -> documents.findProcessStationHandoff(target.targetCode(), name).isPresent());
        }
        if (QUALITY.equals(stage)) {
            return documents.findQualityRequirementCheck(target.targetCode()).isPresent();
        }
        return documents.listOutboundOrders().stream().anyMatch(order -> shipped(order, target));
    }

    private boolean shipped(OutboundOrderRecord order, ResolvedTarget target) {
        if (!"SHIPPED".equals(order.status())) {
            return false;
        }
        if (EvidenceCodes.WORK_ORDER.equals(target.targetType())) {
            return target.targetCode().equals(text(order.workOrderCode()));
        }
        return target.targetCode().equals(text(order.motherOrderCode()))
                && (text(order.workOrderCode()).isEmpty() || target.targetCode().equals(text(order.workOrderCode())));
    }

    private boolean splitMother(String motherOrderCode) {
        if (!documents.listChildWorkOrders(motherOrderCode).isEmpty()) {
            return true;
        }
        return documents.findMotherSplitState(motherOrderCode)
                .map(state -> MotherSplitState.SPLIT.equals(state.decision()))
                .orElse(false);
    }

    private PermissionCode permissionFor(String stage) {
        if (PRODUCTION.equals(stage)) {
            return PermissionCode.BUSINESS_PRODUCTION_REPORT;
        }
        if (QUALITY.equals(stage)) {
            return PermissionCode.BUSINESS_QUALITY_FREEZE;
        }
        return PermissionCode.BUSINESS_OUTBOUND_SHIPMENT;
    }

    private String normalizeStage(String raw) {
        String stage = text(raw).toUpperCase(Locale.ROOT);
        if ("OUTBOUND_SHIPMENT".equals(stage)) {
            throw new BusinessException("发货准备不是出货单");
        }
        if (!STAGES.contains(stage)) {
            throw new BusinessException("过程阶段只允许生产过程、质检过程或发货准备");
        }
        return stage;
    }

    private String normalizeRequestId(String raw) {
        String requestId = text(raw);
        if (!requestId.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new BusinessException("请求编号无效");
        }
        return requestId;
    }

    private String normalizeNote(String raw) {
        return text(raw).replaceAll("\\s+", " ");
    }

    private String normalizeToken(String raw, int max, String label) {
        String value = text(raw);
        checkLength(value, max, label);
        return value;
    }

    private void checkLength(String value, int max, String label) {
        if (value.length() > max) {
            throw new BusinessException(label + "超过可保存长度");
        }
    }

    private RuntimeException missing(AuthUserAccount account) {
        if (account.dataScope() != DataScope.ALL) {
            return new PermissionDeniedException("单号不在当前账号的业务数据范围内");
        }
        return new BusinessException("单据不存在");
    }

    private String nextCode() {
        return "PG-" + CODE_TIME.format(LocalDateTime.now()) + "-" + sequence.incrementAndGet();
    }

    static String contentHash(String stage, String process, String clauseNo, String note) {
        String payload = stage + "\n" + process + "\n" + clauseNo + "\n" + note;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record ResolvedTarget(
            String targetType,
            String targetCode,
            String motherOrderCode,
            List<String> processes
    ) {
        private ResolvedTarget {
            processes = processes == null ? List.of() : List.copyOf(processes);
        }
    }
}
