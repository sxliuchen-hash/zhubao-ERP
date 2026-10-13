package com.zhubao.erp.label.app;

import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobRepository;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    public PrintJob create(String requestId, String businessCode, String templateCode, int copies, String operator) {
        requireRequest(requestId);
        if (businessCode == null || businessCode.isBlank() || templateCode == null || templateCode.isBlank()) {
            throw new BusinessException("打印任务必须指定业务单号和模板");
        }
        if (copies < 1 || copies > 20) {
            throw new BusinessException("打印份数必须在 1 到 20 之间");
        }
        PrintJob existing = jobs.findByRequestId(requestId).orElse(null);
        int version = changes == null ? 0 : changes.latestAppliedVersion(businessCode.trim());
        String snapshot = "LABEL|" + businessCode.trim() + "|" + templateCode.trim() + "|" + copies + "|V" + version;
        if (existing != null) {
            if (!snapshot.equals(existing.contentSnapshot())) {
                throw new BusinessException("同一请求的内容不一致");
            }
            return existing;
        }
        String now = TIME.format(LocalDateTime.now());
        return jobs.save(new PrintJob(
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
    }

    public PrintJob get(String id) {
        return jobs.findById(id).orElseThrow(() -> new BusinessException("打印任务不存在：" + id));
    }

    public PrintJob retry(String id, String requestId, String reason, String operator) {
        PrintJob original = get(id);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("补打必须填写原因");
        }
        PrintJob created = create(requestId, original.businessCode(), original.templateCode(), original.copies(), operator);
        if (!created.id().equals(original.id()) && !original.contentSnapshot().equals(created.contentSnapshot())) {
            created = jobs.save(new PrintJob(
                    created.id(), created.requestId(), created.businessCode(), created.templateCode(), created.copies(),
                    original.contentSnapshot(), created.status(), created.stationId(), created.leaseUntil(),
                    created.failureReason(), original.id(), reason.trim(), created.createdBy(), created.createdAt(), created.updatedAt()
            ));
        } else if (!created.id().equals(original.id())) {
            created = jobs.save(new PrintJob(
                    created.id(), created.requestId(), created.businessCode(), created.templateCode(), created.copies(),
                    created.contentSnapshot(), created.status(), created.stationId(), created.leaseUntil(),
                    created.failureReason(), original.id(), reason.trim(), created.createdBy(), created.createdAt(), created.updatedAt()
            ));
        }
        return created;
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
        return jobs.list();
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
