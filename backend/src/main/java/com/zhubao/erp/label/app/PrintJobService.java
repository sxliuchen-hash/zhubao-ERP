package com.zhubao.erp.label.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 打印任务只记录到系统队列为止。不确定任务保留原状，等待人工核对后再决定补打。
 */
@Service
public class PrintJobService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PrintJobRepository jobs;
    private final String stationToken;
    private final OrderChangeRepository changes;
    private LabelVersionGuard labelGuard;
    private BusinessDocumentRepository documents;
    private BusinessDataAccess dataAccess;
    private LabelService labelService;
    private final ObjectMapper snapshotMapper = new ObjectMapper();

    public PrintJobService(PrintJobRepository jobs, String stationToken) {
        this(jobs, stationToken, null);
    }

    @Autowired
    public PrintJobService(
            PrintJobRepository jobs,
            @Value("${zhubao.print-agent.token:}") String stationToken,
            OrderChangeRepository changes
    ) {
        this.jobs = jobs;
        this.stationToken = stationToken == null ? "" : stationToken.trim();
        this.changes = changes;
    }

    @Autowired(required = false)
    public void setLabelVersionGuard(LabelVersionGuard labelGuard) {
        this.labelGuard = labelGuard;
    }

    @Autowired(required = false)
    public void setDocumentAccess(BusinessDocumentRepository documents, BusinessDataAccess dataAccess) {
        this.documents = documents;
        this.dataAccess = dataAccess;
    }

    @Autowired(required = false)
    public void setLabelService(LabelService labelService) {
        this.labelService = labelService;
    }

    public PrintJob create(String requestId, String businessCode, String templateCode, int copies, AuthUserAccount account) {
        assertPrintable(businessCode, account);
        return create(requestId, businessCode, templateCode, copies, account == null ? "" : account.operator());
    }

    public String prepareBatchSnapshot(String businessCode, String templateCode, int copies, AuthUserAccount account) {
        if (businessCode == null || businessCode.isBlank() || templateCode == null || templateCode.isBlank()
                || copies < 1 || copies > 20) throw new BusinessException("打印单号、模板或份数无效");
        assertPrintable(businessCode, account);
        int version = changes == null ? 0 : changes.latestAppliedVersion(businessCode.trim());
        return snapshotForJob(businessCode.trim(), templateCode.trim(), copies, version);
    }

    public void assertBatchReadable(String businessCode, AuthUserAccount account) {
        assertPrintable(businessCode, account);
    }

    public PrintJob get(String id, AuthUserAccount account) {
        PrintJob job = get(id);
        assertPrintable(job.businessCode(), account);
        return job;
    }

    public List<PrintJob> list(AuthUserAccount account) {
        return list().stream().filter(job -> readable(job.businessCode(), account)).toList();
    }

    public PrintJob create(String requestId, String businessCode, String templateCode, int copies, String operator) {
        requireRequest(requestId);
        if (businessCode == null || businessCode.isBlank() || templateCode == null || templateCode.isBlank()) {
            throw new BusinessException("打印任务必须指定业务单号和模板");
        }
        if (copies < 1 || copies > 20) {
            throw new BusinessException("打印份数必须在 1 到 20 之间");
        }
        int version = changes == null ? 0 : changes.latestAppliedVersion(businessCode.trim());
        String snapshot = snapshotForJob(businessCode.trim(), templateCode.trim(), copies, version);
        String now = TIME.format(LocalDateTime.now());
        PrintJob stored = jobs.insertIfAbsent(new PrintJob(
                "PJ-" + UUID.randomUUID().toString().substring(0, 8),
                requestId.trim(),
                businessCode.trim(),
                templateCode.trim(),
                copies,
                snapshot,
                PrintJobStatus.PENDING,
                "",
                "",
                "",
                "",
                "",
                operator == null ? "" : operator.trim(),
                now,
                now
        ));
        if (!snapshot.equals(stored.contentSnapshot())) {
            throw new BusinessException("同一请求的内容不一致");
        }
        if (stored.id().startsWith("PJ-") && labelGuard != null && stored.createdAt().equals(now)) {
            labelGuard.recordEvent(businessCode.trim(), businessCode.trim(), version == 0 ? "PRINT" : "REISSUE_VERSION",
                    "V" + version, "", stored.id(), "", operator);
        }
        return stored;
    }

    public PrintJob get(String id) {
        expireLeases(TIME.format(LocalDateTime.now()));
        return jobs.findById(id).orElseThrow(() -> new BusinessException("打印任务不存在：" + id));
    }

    public PrintJob retry(String id, String requestId, String reason, String operator) {
        PrintJob original = jobs.findById(id).orElseThrow(() -> new BusinessException("打印任务不存在：" + id));
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("补打必须填写原因");
        }
        requireRequest(requestId);
        String now = TIME.format(LocalDateTime.now());
        PrintJob stored = jobs.insertIfAbsent(new PrintJob(
                "PJ-" + UUID.randomUUID().toString().substring(0, 8),
                requestId.trim(),
                original.businessCode(),
                original.templateCode(),
                original.copies(),
                original.contentSnapshot(),
                PrintJobStatus.PENDING,
                "",
                "",
                "",
                original.id(),
                reason.trim(),
                operator == null ? "" : operator.trim(),
                now,
                now
        ));
        if (!original.contentSnapshot().equals(stored.contentSnapshot())) {
            throw new BusinessException("同一请求的内容不一致");
        }
        if (labelGuard != null && stored.createdAt().equals(now)) {
            labelGuard.recordEvent(original.businessCode(), original.businessCode(), "REPRINT_SAME", "", "", stored.id(), reason.trim(), operator);
        }
        return stored;
    }

    public PrintJob verifyPaper(String id, String requestId, String note, String operator) {
        PrintJob job = get(id);
        if (note == null || note.isBlank()) {
            throw new BusinessException("出纸核实必须填写结果");
        }
        if (labelGuard != null) {
            labelGuard.recordEvent(job.businessCode(), job.businessCode(), "PAPER_CHECKED", "", "", job.id(), note.trim(), operator);
        }
        return job;
    }

    public PrintJob relabel(String id, String requestId, String note, String operator) {
        PrintJob job = get(id);
        if (note == null || note.isBlank()) {
            throw new BusinessException("实物换贴必须填写结果");
        }
        if (labelGuard != null) {
            labelGuard.recordEvent(job.businessCode(), job.businessCode(), "RELABELED", "", "", job.id(), note.trim(), operator);
        }
        return job;
    }

    public PrintJob claim(String stationId, String token) {
        assertStation(stationId, token);
        expireLeases(TIME.format(LocalDateTime.now()));
        return jobs.claim(stationId.trim(), TIME.format(LocalDateTime.now().plusMinutes(2)), TIME.format(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("当前没有待打印任务"));
    }

    public PrintJob report(String id, String stationId, String token, String outcome, String failureReason) {
        assertStation(stationId, token);
        PrintJob job = get(id);
        if (!stationId.equals(job.stationId()) || job.status() != PrintJobStatus.LEASED) {
            throw new BusinessException("只能回报本工位已领取的任务");
        }
        PrintJobStatus next = switch (outcome == null ? "" : outcome.trim()) {
            case "SPOOLED" -> PrintJobStatus.SPOOLED;
            case "FAILED" -> PrintJobStatus.FAILED;
            default -> throw new BusinessException("只能回报已送入系统队列或发送前失败");
        };
        if (next == PrintJobStatus.FAILED && (failureReason == null || failureReason.isBlank())) {
            throw new BusinessException("发送前失败必须填写原因");
        }
        return replace(job, next, job.leaseUntil(), failureReason == null ? "" : failureReason.trim());
    }

    public int expireLeases(String now) {
        int changed = 0;
        for (PrintJob job : jobs.list()) {
            if (job.status() == PrintJobStatus.LEASED && !job.leaseUntil().isBlank() && job.leaseUntil().compareTo(now) < 0) {
                replace(job, PrintJobStatus.UNCERTAIN, job.leaseUntil(), "租约到期，未能确认是否已送入队列");
                changed++;
            }
        }
        return changed;
    }

    public List<PrintJob> list() {
        expireLeases(TIME.format(LocalDateTime.now()));
        return jobs.list();
    }

    private String snapshotForJob(String businessCode, String templateCode, int copies, int version) {
        if (documents == null || labelService == null) {
            return snapshot(businessCode, templateCode, copies, version);
        }
        String selectedTemplate = "FLOW".equals(templateCode) ? null : templateCode;
        LabelPayload payload;
        if (documents.findMotherOrderByCode(businessCode).isPresent()) {
            payload = labelService.generateMotherLabel(businessCode, selectedTemplate);
        } else if (documents.findChildWorkOrderByCode(businessCode).isPresent()) {
            payload = labelService.generateWorkOrderLabel(businessCode, selectedTemplate);
        } else {
            return snapshot(businessCode, templateCode, copies, version);
        }
        String[] qrParts = payload.qrContent().split("\\|", -1);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("kind", "LABEL");
        snapshot.put("businessCode", businessCode);
        snapshot.put("templateCode", payload.templateCode());
        snapshot.put("copies", copies);
        snapshot.put("widthMm", payload.printerSetting().widthMm());
        snapshot.put("heightMm", payload.printerSetting().heightMm());
        snapshot.put("dpi", payload.printerSetting().dpi());
        snapshot.put("version", "V" + version);
        snapshot.put("instance", qrParts.length > 4 ? qrParts[4] : "");
        snapshot.put("qr", payload.qrContent());
        snapshot.put("brandTitle", payload.brandTitle());
        snapshot.put("typeLabel", payload.typeLabel());
        snapshot.put("primaryCode", payload.primaryCode());
        snapshot.put("fields", payload.fields());
        snapshot.put("footerLeft", payload.footerLeft());
        snapshot.put("footerRight", payload.footerRight());
        snapshot.put("layout", payload.layout());
        snapshot.put("lines", payload.fields().stream()
                .map(this::lineText).toList());
        try {
            return snapshotMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法生成标签打印快照", ex);
        }
    }

    private String lineText(LabelField field) {
        return field.name() + ":" + field.value();
    }

    /** 结构化标签快照。客户端不能改写其中的单号、版本和二维码。 */
    static String snapshot(String businessCode, String templateCode, int copies, int version) {
        String instance = "LI" + Integer.toHexString(Math.abs((businessCode + "|" + templateCode + "|" + version).hashCode()));
        String scanType = businessCode.startsWith("MD") ? "mother" : "work_order";
        String qr = "JG|" + scanType + "|" + businessCode + "|" + version + "|" + instance;
        return "{\"kind\":\"LABEL\",\"businessCode\":\"" + escape(businessCode)
                + "\",\"templateCode\":\"" + escape(templateCode)
                + "\",\"copies\":" + copies
                + ",\"widthMm\":40,\"heightMm\":30"
                + ",\"version\":\"V" + version
                + "\",\"instance\":\"" + instance
                + "\",\"qr\":\"" + qr
                + "\",\"lines\":[\"单号:" + escape(businessCode) + "\",\"模板:" + escape(templateCode) + "\"]}";
    }

    private void assertPrintable(String businessCode, AuthUserAccount account) {
        if (!readable(businessCode, account)) {
            throw new PermissionDeniedException("打印对象不在当前账号的业务数据范围内");
        }
    }

    private boolean readable(String businessCode, AuthUserAccount account) {
        if (documents != null) {
            boolean known = documents.findMotherOrderByCode(businessCode).isPresent()
                    || documents.findChildWorkOrderByCode(businessCode).isPresent();
            if (!known) {
                throw new BusinessException("打印对象不存在：" + businessCode);
            }
        }
        if (account == null || dataAccess == null) {
            return true;
        }
        try {
            dataAccess.assertCanRead(BusinessDataAccess.contextOf(account), businessCode);
            return true;
        } catch (PermissionDeniedException ex) {
            return false;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private PrintJob replace(PrintJob job, PrintJobStatus status, String leaseUntil, String failureReason) {
        return jobs.save(new PrintJob(
                job.id(), job.requestId(), job.businessCode(), job.templateCode(), job.copies(), job.contentSnapshot(),
                status, job.stationId(), leaseUntil, failureReason, job.predecessorId(), job.retryReason(),
                job.createdBy(), job.createdAt(), TIME.format(LocalDateTime.now())
        ));
    }

    private void assertStation(String stationId, String token) {
        if (stationToken.isBlank()) {
            throw new BusinessException("打印工位凭据尚未配置");
        }
        if (stationId == null || stationId.isBlank() || token == null || !stationToken.equals(token)) {
            throw new PermissionDeniedException("打印工位凭据无效");
        }
    }

    private void requireRequest(String requestId) {
        if (requestId == null || !requestId.trim().matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new BusinessException("请求编号无效");
        }
    }
}
