package com.zhubao.erp.label.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintRoutingRepository;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTarget;
import com.zhubao.erp.label.domain.PrintTargetJob;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

@Service
public class PrintBatchService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final PrintRoutingRepository repository;
    private final PrintJobService labels;
    private final ObjectMapper mapper = new ObjectMapper();

    public PrintBatchService(PrintRoutingRepository repository, PrintJobService labels) {
        this.repository = repository;
        this.labels = labels;
    }

    public List<PrintStation> stations() { return repository.stations().stream().map(PrintStation::publicView).toList(); }
    public List<PrintQueue> queues() { return repository.queues(); }
    public List<PrintQueue> availableQueues() {
        Set<String> active = repository.stations().stream().filter(PrintStation::enabled)
                .map(PrintStation::stationId).collect(java.util.stream.Collectors.toSet());
        return repository.queues().stream().filter(q -> q.enabled() && active.contains(q.stationId())).toList();
    }

    public StationCredential saveStation(PrintStation input) {
        if (input == null || !validId(input.stationId()) || input.name() == null || input.name().isBlank())
            throw new BusinessException("工位编号或名称无效");
        PrintStation previous = repository.station(input.stationId()).orElse(null);
        String secret = previous == null ? "PS-" + UUID.randomUUID() + UUID.randomUUID() : "";
        String hash = previous == null ? digest(secret) : previous.tokenHash();
        PrintStation saved = repository.saveStation(new PrintStation(input.stationId(), input.name().trim(),
                input.enabled(), input.remark() == null ? "" : input.remark().trim(), hash));
        return new StationCredential(saved.publicView(), secret);
    }
    public StationCredential rotate(String stationId) {
        PrintStation previous = repository.station(stationId).orElseThrow(() -> new BusinessException("工位不存在"));
        String secret = "PS-" + UUID.randomUUID() + UUID.randomUUID();
        repository.saveStation(new PrintStation(previous.stationId(), previous.name(), previous.enabled(),
                previous.remark(), digest(secret)));
        return new StationCredential(previous.publicView(), secret);
    }
    public PrintQueue saveQueue(PrintQueue queue) {
        if (queue == null || !validId(queue.stationId()) || !validId(queue.queueId())
                || queue.name() == null || queue.name().isBlank() || queue.widthMm() < 10
                || queue.heightMm() < 10 || queue.widthMm() > 200 || queue.heightMm() > 200
                || !List.of("MOTHER", "WORK_ORDER", "ALL").contains(queue.labelType()))
            throw new BusinessException("打印队列配置无效");
        repository.station(queue.stationId()).orElseThrow(() -> new BusinessException("工位不存在"));
        return repository.saveQueue(new PrintQueue(queue.stationId(), queue.queueId(), queue.name().trim(),
                queue.labelType(), queue.widthMm(), queue.heightMm(), queue.enabled(), queue.defaultQueue()));
    }

    public PrintBatch create(String requestId, String businessCode, String templateCode, int copies,
                             List<PrintTarget> requested, AuthUserAccount account) {
        requireRequest(requestId);
        if (requested == null || requested.isEmpty() || requested.size() > 20)
            throw new BusinessException("请选择 1 到 20 个打印目标");
        if (copies < 1 || copies > 20) throw new BusinessException("每目标份数须为 1 到 20");
        List<PrintTarget> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PrintTarget target : requested) {
            if (target == null || !validId(target.stationId()) || !validId(target.queueId()))
                throw new BusinessException("打印目标无效");
            if (seen.add(target.stationId() + "|" + target.queueId())) targets.add(target);
        }
        targets.sort(java.util.Comparator.comparing(PrintTarget::stationId).thenComparing(PrintTarget::queueId));
        String snapshot = labels.prepareBatchSnapshot(businessCode, templateCode, copies, account);
        String key = businessCode.trim() + "|" + templateCode.trim() + "|" + copies + "|" + snapshot + "|" + targets;
        String hash = digest(key);
        PrintBatch existing = repository.batchByRequest(requestId.trim()).orElse(null);
        if (existing != null) return sameOrReject(existing, hash);
        validateTargets(targets, snapshot, businessCode);
        String now = TIME.format(LocalDateTime.now());
        String batchId = "PB-" + UUID.randomUUID();
        List<PrintTargetJob> jobs = targets.stream().map(target -> new PrintTargetJob(
                "PJ-" + UUID.randomUUID(), batchId, target.stationId(), target.queueId(), copies,
                PrintJobStatus.PENDING, "", "", "", "", "", "", now, now)).toList();
        PrintBatch inserted = repository.insertBatch(new PrintBatch(batchId, requestId.trim(), businessCode.trim(),
                templateCode.trim(), copies, hash, snapshot, account.operator(), now, jobs));
        return sameOrReject(inserted, hash);
    }
    public PrintBatch batch(String id, AuthUserAccount account) {
        PrintBatch batch = repository.batch(id).orElseThrow(() -> new BusinessException("打印批次不存在"));
        labels.assertBatchReadable(batch.businessCode(), account);
        return batch;
    }
    public PrintTargetJob paperCheck(String id, String result, AuthUserAccount account) {
        PrintTargetJob job = repository.job(id).orElseThrow(() -> new BusinessException("目标任务不存在"));
        batch(job.batchId(), account);
        if (result == null || result.isBlank()) throw new BusinessException("请填写出纸核实结果");
        if (job.status() != PrintJobStatus.SPOOLED && job.status() != PrintJobStatus.UNCERTAIN
                && job.status() != PrintJobStatus.FAILED) throw new BusinessException("当前任务尚不能核实出纸");
        return repository.saveJob(job.withStatus(job.status(), job.claimedStationId(), job.leaseUntil(),
                job.failureReason(), result.trim(), TIME.format(LocalDateTime.now())));
    }
    public ClaimedPrintJob claim(String stationId, String token) {
        authenticate(stationId, token);
        String now = TIME.format(LocalDateTime.now());
        expireLeases(now);
        for (int attempts = 0; attempts < 100; attempts++) {
            PrintTargetJob job = repository.claim(stationId, TIME.format(LocalDateTime.now().plusMinutes(2)), now)
                    .orElseThrow(() -> new BusinessException("当前没有待打印任务"));
            PrintBatch batch = repository.batch(job.batchId()).orElseThrow();
            try {
                validateTargets(List.of(new PrintTarget(job.targetStationId(), job.targetQueueId())),
                        batch.contentSnapshot(), batch.businessCode());
                return new ClaimedPrintJob(job, batch.contentSnapshot());
            } catch (BusinessException ex) {
                repository.saveJobIfStatus(job.withStatus(PrintJobStatus.FAILED, stationId, job.leaseUntil(),
                        ex.getMessage(), "", now), PrintJobStatus.LEASED);
            }
        }
        throw new BusinessException("待打印目标配置连续失效，请检查工位与队列");
    }
    public PrintTargetJob report(String id, String stationId, String token, String outcome, String reason) {
        authenticate(stationId, token);
        PrintTargetJob job = repository.job(id).orElseThrow(() -> new BusinessException("目标任务不存在"));
        if (!stationId.equals(job.targetStationId()) || !stationId.equals(job.claimedStationId())
                || job.status() != PrintJobStatus.LEASED) throw new BusinessException("只能回报本工位领取的目标任务");
        String now = TIME.format(LocalDateTime.now());
        if (!job.leaseUntil().isBlank() && job.leaseUntil().compareTo(now) < 0) {
            repository.expireLeases(now);
            throw new BusinessException("任务租约已到期，请先人工核纸");
        }
        PrintJobStatus next = switch (outcome == null ? "" : outcome) {
            case "SPOOLED" -> PrintJobStatus.SPOOLED;
            case "FAILED" -> PrintJobStatus.FAILED;
            default -> throw new BusinessException("目标任务回报状态无效");
        };
        if (next == PrintJobStatus.FAILED && (reason == null || reason.isBlank()))
            throw new BusinessException("失败时请填写原因");
        return repository.saveJobIfStatus(job.withStatus(next, stationId, job.leaseUntil(),
                reason == null ? "" : reason.trim(), job.paperCheckResult(), now),
                PrintJobStatus.LEASED).orElseThrow(() -> new BusinessException("任务状态已变化，请先人工核纸"));
    }
    public int expireLeases(String now) {
        // 租约超时后不自动重投，必须人工核纸。
        return repository.expireLeases(now);
    }

    private void validateTargets(List<PrintTarget> targets, String snapshot, String businessCode) {
        try {
            JsonNode root = mapper.readTree(snapshot);
            int width = root.path("widthMm").asInt(); int height = root.path("heightMm").asInt();
            String type = businessCode.startsWith("MD") ? "MOTHER" : "WORK_ORDER";
            List<PrintQueue> active = availableQueues();
            for (PrintTarget target : targets) {
                boolean valid = active.stream().anyMatch(queue -> queue.stationId().equals(target.stationId())
                        && queue.queueId().equals(target.queueId())
                        && (queue.labelType().equals(type) || queue.labelType().equals("ALL"))
                        && queue.widthMm() == width && queue.heightMm() == height);
                if (!valid) throw new BusinessException("打印目标停用、标签类型或纸张规格不匹配："
                        + target.stationId() + "/" + target.queueId());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BusinessException("标签快照无效"); }
    }
    private void authenticate(String stationId, String token) {
        PrintStation station = repository.station(stationId).orElseThrow(() -> new PermissionDeniedException("工位凭据无效"));
        if (!station.enabled() || token == null || !MessageDigest.isEqual(
                station.tokenHash().getBytes(StandardCharsets.UTF_8), digest(token).getBytes(StandardCharsets.UTF_8)))
            throw new PermissionDeniedException("工位凭据无效");
    }
    private PrintBatch sameOrReject(PrintBatch batch, String hash) {
        if (!batch.payloadHash().equals(hash)) throw new BusinessException("同一请求的打印目标或内容不一致");
        return batch;
    }
    private void requireRequest(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._:-]{8,64}")) throw new BusinessException("请求编号无效");
    }
    private boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9._:-]{1,64}"); }
    private String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public record StationCredential(PrintStation station, String token) {}
    public record ClaimedPrintJob(PrintTargetJob job, String contentSnapshot) {}
}
