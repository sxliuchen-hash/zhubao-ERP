package com.zhubao.erp.operations.app;

import com.zhubao.erp.operations.domain.OperationsCenterSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class OperationsCenterService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Environment environment;
    private final ObjectProvider<DataSource> dataSources;
    private final String frontendUrl;
    private final String frontendProbeUrl;
    private final String backupRoot;
    private final String logFile;
    private final int frontendProbeTimeoutMs;
    private final int backupRpoHours;

    public OperationsCenterService(
            Environment environment,
            ObjectProvider<DataSource> dataSources,
            @Value("${zhubao.operations.frontend-url:http://localhost:5173/}") String frontendUrl,
            @Value("${zhubao.operations.frontend-probe-url:}") String frontendProbeUrl,
            @Value("${zhubao.operations.backup-root:}") String backupRoot,
            @Value("${zhubao.operations.log-file:}") String logFile,
            @Value("${zhubao.operations.frontend-probe-timeout-ms:800}") int frontendProbeTimeoutMs,
            @Value("${zhubao.operations.backup-rpo-hours:24}") int backupRpoHours
    ) {
        this.environment = environment;
        this.dataSources = dataSources;
        this.frontendUrl = frontendUrl;
        this.frontendProbeUrl = frontendProbeUrl;
        this.backupRoot = backupRoot;
        this.logFile = logFile;
        this.frontendProbeTimeoutMs = frontendProbeTimeoutMs;
        this.backupRpoHours = backupRpoHours;
    }

    public OperationsCenterSnapshot snapshot() {
        Instant now = Instant.now();
        DatabaseProbe database = probeDatabase(now);
        FrontendProbe frontend = probeFrontend();
        PwaProbe pwa = probePwaAssets();
        LogProbe logs = probeLogs(now);
        List<OperationsCenterSnapshot.BackupStatus> localBackups = probeBackups(now);
        CosBackupProbe cosBackup = probeCosBackup();
        List<OperationsCenterSnapshot.BackupStatus> backups = new ArrayList<>(localBackups);
        backups.add(cosBackupStatus(cosBackup, now));
        List<OperationsCenterSnapshot.ServiceStatus> services = serviceStatuses(database, frontend, pwa, localBackups, cosBackup, now);
        List<OperationsCenterSnapshot.ErrorSummary> errors = errorSummaries(now, logs);

        long degradedServices = services.stream().filter(service -> !"green".equals(service.tone())).count();
        int criticalErrors = (int) errors.stream().filter(error -> "red".equals(error.tone())).count();
        String backupStatus = backups.stream().allMatch(this::backupToneAcceptable) ? "正常" : "需配置";
        String overallStatus = criticalErrors > 0 || degradedServices > 0 ? "ATTENTION" : "ONLINE";

        return new OperationsCenterSnapshot(
                DISPLAY_TIME.format(now),
                new OperationsCenterSnapshot.Summary(
                        overallStatus,
                        services.size(),
                        (int) degradedServices,
                        errors.stream().mapToInt(OperationsCenterSnapshot.ErrorSummary::count).sum(),
                        criticalErrors,
                        backupStatus,
                        ManagementFactory.getRuntimeMXBean().getUptime() / 60_000
                ),
                services,
                runtimeMetrics(),
                errors,
                logs.entries(),
                backups,
                checklist(database, frontend, pwa, logs, cosBackup)
        );
    }

    private List<OperationsCenterSnapshot.ServiceStatus> serviceStatuses(
            DatabaseProbe database,
            FrontendProbe frontend,
            PwaProbe pwa,
            List<OperationsCenterSnapshot.BackupStatus> localBackups,
            CosBackupProbe cosBackup,
            Instant now
    ) {
        List<OperationsCenterSnapshot.ServiceStatus> services = new ArrayList<>();
        String checkedAt = DISPLAY_TIME.format(now);
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "BACKEND",
                "后端 API",
                "ONLINE",
                "/api/operations/center",
                "Spring Boot 运行中，Actuator 作为底层健康与指标参考。",
                "平台负责人",
                checkedAt,
                "green"
        ));
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "FRONTEND",
                "前端 PWA",
                frontend.status(),
                frontendUrl,
                frontend.detail(),
                "前端负责人",
                checkedAt,
                frontend.tone()
        ));
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "PWA_ASSETS",
                "PWA 静态资源",
                pwa.status(),
                pwa.endpoint(),
                pwa.detail(),
                "前端负责人",
                checkedAt,
                pwa.tone()
        ));
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "DATABASE",
                "数据库连接",
                database.status(),
                database.endpoint(),
                database.detail(),
                "后端负责人",
                checkedAt,
                database.tone()
        ));
        String backupTone = localBackups.stream().allMatch(this::backupToneAcceptable) ? "green" : "amber";
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "BACKUP",
                "数据库备份",
                "green".equals(backupTone) ? "ONLINE" : "NEEDS_CONFIG",
                blankToReviewText(backupRoot, "未配置 zhubao.operations.backup-root"),
                "只展示备份状态，不在页面触发备份命令，避免误操作生产数据。",
                "运维负责人",
                checkedAt,
                backupTone
        ));
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "COS_BACKUP",
                "腾讯云 COS 备份",
                cosBackup.status(),
                cosBackup.storage(),
                cosBackup.detail(),
                "运维负责人",
                checkedAt,
                cosBackup.tone()
        ));
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "ATTACHMENT",
                "附件对象存储",
                "REVIEW",
                environment.getProperty("zhubao.documents.storage.provider", "local"),
                "当前默认本地对象存储适配，后续切 NAS/S3 签名 URL。",
                "平台负责人",
                checkedAt,
                "amber"
        ));
        services.add(new OperationsCenterSnapshot.ServiceStatus(
                "HARDWARE",
                "软硬件联动",
                "POC",
                "/api/hardware/summary",
                "扫码、NVR、标签打印、电子秤按兼容优先和失败兜底推进。",
                "现场实施负责人",
                checkedAt,
                "amber"
        ));
        return services;
    }

    private List<OperationsCenterSnapshot.RuntimeMetric> runtimeMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        int memoryPercent = maxMemory > 0 ? (int) Math.round(usedMemory * 100.0 / maxMemory) : 0;
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double loadAverage = osBean.getSystemLoadAverage();

        return List.of(
                new OperationsCenterSnapshot.RuntimeMetric(
                        "JVM_MEMORY",
                        "JVM 内存",
                        String.valueOf(memoryPercent),
                        "%",
                        toMiB(usedMemory) + " / " + toMiB(maxMemory) + " MiB",
                        memoryPercent >= 85 ? "red" : memoryPercent >= 70 ? "amber" : "green"
                ),
                new OperationsCenterSnapshot.RuntimeMetric(
                        "THREADS",
                        "线程数",
                        String.valueOf(threadBean.getThreadCount()),
                        "个",
                        "峰值 " + threadBean.getPeakThreadCount(),
                        threadBean.getThreadCount() > 200 ? "amber" : "blue"
                ),
                new OperationsCenterSnapshot.RuntimeMetric(
                        "PROCESSORS",
                        "CPU 核心",
                        String.valueOf(runtime.availableProcessors()),
                        "核",
                        loadAverage >= 0 ? "系统负载 " + String.format(Locale.ROOT, "%.2f", loadAverage) : "系统负载不可用",
                        "blue"
                ),
                new OperationsCenterSnapshot.RuntimeMetric(
                        "UPTIME",
                        "后端运行",
                        String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime() / 60_000),
                        "分钟",
                        ManagementFactory.getRuntimeMXBean().getName(),
                        "green"
                )
        );
    }

    private DatabaseProbe probeDatabase(Instant now) {
        DataSource dataSource = dataSources.getIfAvailable();
        if (dataSource == null) {
            return new DatabaseProbe(
                    "MEMORY_MODE",
                    "未配置 JDBC",
                    "当前默认内存仓储运行，MySQL profile 未启用。",
                    "memory repositories",
                    "amber"
            );
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            boolean valid = connection.isValid(2);
            return new DatabaseProbe(
                    valid ? "ONLINE" : "DEGRADED",
                    valid ? "连接正常" : "连接不可用",
                    metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion(),
                    metaData.getURL(),
                    valid ? "green" : "red"
            );
        } catch (SQLException error) {
            return new DatabaseProbe(
                    "DOWN",
                    "连接失败",
                    error.getClass().getSimpleName() + ": " + truncate(error.getMessage(), 180),
                    "spring.datasource",
                    "red"
            );
        }
    }

    private FrontendProbe probeFrontend() {
        if (frontendProbeUrl == null || frontendProbeUrl.isBlank()) {
            return new FrontendProbe(
                    "NOT_CONFIGURED",
                    "未配置前端探活地址，运维中心只能展示前端公开访问地址。",
                    blankToReviewText(frontendUrl, "未配置 zhubao.operations.frontend-url"),
                    "amber"
            );
        }

        URI uri;
        try {
            uri = URI.create(frontendProbeUrl);
        } catch (IllegalArgumentException error) {
            return new FrontendProbe(
                    "CONFIG_ERROR",
                    "前端探活地址格式错误：" + truncate(error.getMessage(), 120),
                    frontendProbeUrl,
                    "red"
            );
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return new FrontendProbe(
                    "CONFIG_ERROR",
                    "前端探活只支持 HTTP/HTTPS 地址。",
                    frontendProbeUrl,
                    "red"
            );
        }

        int timeoutMs = Math.max(200, frontendProbeTimeoutMs);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "zhubao-operations-center")
                .GET()
                .build();

        long start = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 400) {
                return new FrontendProbe(
                        "ONLINE",
                        "前端探活成功，HTTP " + statusCode + "，耗时 " + elapsedMs + " ms。",
                        frontendProbeUrl,
                        "green"
                );
            }
            return new FrontendProbe(
                    statusCode >= 500 ? "DOWN" : "DEGRADED",
                    "前端探活返回 HTTP " + statusCode + "，耗时 " + elapsedMs + " ms。",
                    frontendProbeUrl,
                    statusCode >= 500 ? "red" : "amber"
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new FrontendProbe(
                    "DOWN",
                    "前端探活被中断：" + truncate(error.getMessage(), 120),
                    frontendProbeUrl,
                    "red"
            );
        } catch (IOException error) {
            return new FrontendProbe(
                    "DOWN",
                    error.getClass().getSimpleName() + ": " + truncate(error.getMessage(), 160),
                    frontendProbeUrl,
                    "red"
            );
        }
    }

    private PwaProbe probePwaAssets() {
        if (frontendProbeUrl == null || frontendProbeUrl.isBlank()) {
            return new PwaProbe(
                    "NOT_CONFIGURED",
                    "未配置前端探活地址，无法检查 Manifest 和 Service Worker。",
                    "zhubao.operations.frontend-probe-url 未配置",
                    "amber"
            );
        }

        URI baseUri;
        try {
            baseUri = normalizeBaseUri(frontendProbeUrl);
        } catch (IllegalArgumentException error) {
            return new PwaProbe(
                    "CONFIG_ERROR",
                    "前端探活地址格式错误：" + truncate(error.getMessage(), 120),
                    frontendProbeUrl,
                    "red"
            );
        }

        String scheme = baseUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return new PwaProbe(
                    "CONFIG_ERROR",
                    "PWA 静态资源探活只支持 HTTP/HTTPS 地址。",
                    frontendProbeUrl,
                    "red"
            );
        }

        int timeoutMs = Math.max(200, frontendProbeTimeoutMs);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        URI manifestUri = baseUri.resolve("manifest.webmanifest");
        URI serviceWorkerUri = baseUri.resolve("sw.js");

        try {
            AssetProbe manifest = fetchAsset(client, manifestUri, timeoutMs);
            AssetProbe serviceWorker = fetchAsset(client, serviceWorkerUri, timeoutMs);
            boolean manifestHealthy = manifest.success()
                    && manifest.body().contains("\"name\"")
                    && manifest.body().contains("\"start_url\"");
            boolean serviceWorkerHealthy = serviceWorker.success()
                    && serviceWorker.body().contains("addEventListener('install'")
                    && serviceWorker.body().contains("addEventListener('activate'");

            if (manifestHealthy && serviceWorkerHealthy) {
                return new PwaProbe(
                        "ONLINE",
                        "Manifest 和 Service Worker 可访问，manifest HTTP " + manifest.statusCode()
                                + "，sw HTTP " + serviceWorker.statusCode() + "。",
                        manifestUri + " / " + serviceWorkerUri,
                        "green"
                );
            }

            return new PwaProbe(
                    "DEGRADED",
                    "PWA 静态资源不完整：manifest HTTP " + manifest.statusCode()
                            + "，sw HTTP " + serviceWorker.statusCode() + "。",
                    manifestUri + " / " + serviceWorkerUri,
                    "amber"
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new PwaProbe(
                    "DOWN",
                    "PWA 静态资源探活被中断：" + truncate(error.getMessage(), 120),
                    manifestUri + " / " + serviceWorkerUri,
                    "red"
            );
        } catch (IOException error) {
            return new PwaProbe(
                    "DOWN",
                    error.getClass().getSimpleName() + ": " + truncate(error.getMessage(), 160),
                    manifestUri + " / " + serviceWorkerUri,
                    "red"
            );
        }
    }

    private URI normalizeBaseUri(String rawUrl) {
        String value = rawUrl.endsWith("/") ? rawUrl : rawUrl + "/";
        return URI.create(value);
    }

    private AssetProbe fetchAsset(HttpClient client, URI uri, int timeoutMs) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "zhubao-operations-center")
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        return new AssetProbe(response.statusCode(), response.body());
    }

    private List<OperationsCenterSnapshot.BackupStatus> probeBackups(Instant now) {
        if (backupRoot == null || backupRoot.isBlank()) {
            return List.of(new OperationsCenterSnapshot.BackupStatus(
                    "DB-BACKUP-POC",
                    "MySQL 逻辑备份",
                    "未接入真实目录",
                    "评审样例：2026-07-06 23:30:00",
                    DISPLAY_TIME.format(now.plusSeconds(backupRpoHours * 3600L)),
                    "zhubao.operations.backup-root 未配置",
                    "未计算",
                    "未计算",
                    "生产环境建议记录 mysqldump 输出、备份大小、checksum 和恢复演练结果。",
                    "amber"
            ));
        }

        Path root = Path.of(backupRoot);
        if (!Files.exists(root)) {
            return List.of(new OperationsCenterSnapshot.BackupStatus(
                    "DB-BACKUP-MISSING",
                    "MySQL 逻辑备份",
                    "目录不存在",
                    "无",
                    DISPLAY_TIME.format(now.plusSeconds(backupRpoHours * 3600L)),
                    root.toString(),
                    "未计算",
                    "未计算",
                    "已配置备份目录但当前无法读取，需要运维确认挂载和权限。",
                    "red"
            ));
        }

        try (Stream<Path> files = Files.list(root)) {
            Optional<Path> latest = files
                    .filter(Files::isRegularFile)
                    .filter(this::isBackupFile)
                    .max(Comparator.comparing(this::lastModifiedOrEpoch));
            if (latest.isEmpty()) {
                return List.of(new OperationsCenterSnapshot.BackupStatus(
                        "DB-BACKUP-EMPTY",
                        "MySQL 逻辑备份",
                        "未发现备份文件",
                        "无",
                        DISPLAY_TIME.format(now.plusSeconds(backupRpoHours * 3600L)),
                        root.toString(),
                        "未计算",
                        "未计算",
                        "目录存在但没有 .sql/.dump/.backup/.gz 文件。",
                        "red"
                ));
            }
            Path latestFile = latest.get();
            Instant lastModified = lastModifiedOrEpoch(latestFile);
            Instant nextDueAt = lastModified.plusSeconds(backupRpoHours * 3600L);
            boolean overdue = nextDueAt.isBefore(now);
            String fileSize = readableSize(sizeOrZero(latestFile));
            String checksum = sha256(latestFile);
            return List.of(new OperationsCenterSnapshot.BackupStatus(
                    "DB-BACKUP-LATEST",
                    "MySQL 逻辑备份",
                    overdue ? "超过 RPO" : "已发现备份",
                    DISPLAY_TIME.format(lastModified),
                    DISPLAY_TIME.format(nextDueAt),
                    latestFile.toString(),
                    fileSize,
                    checksum,
                    "最近备份文件 " + latestFile.getFileName() + "，大小 " + fileSize + "，" + checksum,
                    overdue ? "amber" : "green"
            ));
        } catch (IOException error) {
            return List.of(new OperationsCenterSnapshot.BackupStatus(
                    "DB-BACKUP-ERROR",
                    "MySQL 逻辑备份",
                    "读取失败",
                    "无",
                    DISPLAY_TIME.format(now.plusSeconds(backupRpoHours * 3600L)),
                    root.toString(),
                    "未计算",
                    "未计算",
                    error.getClass().getSimpleName() + ": " + truncate(error.getMessage(), 160),
                    "red"
            ));
        }
    }

    private CosBackupProbe probeCosBackup() {
        boolean enabled = Boolean.parseBoolean(environment.getProperty("zhubao.operations.cos.enabled", "false"));
        String bucket = environment.getProperty("zhubao.operations.cos.bucket", "");
        String region = environment.getProperty("zhubao.operations.cos.region", "");
        String prefix = environment.getProperty("zhubao.operations.cos.prefix", "");
        String credentialsMode = environment.getProperty("zhubao.operations.cos.credentials-mode", "");
        String lifecyclePolicy = environment.getProperty("zhubao.operations.cos.lifecycle-policy", "");

        if (!enabled) {
            return new CosBackupProbe(
                    "COS-BACKUP-DISABLED",
                    "DISABLED",
                    "未启用",
                    "腾讯云 COS 异地备份",
                    "未启用",
                    "未配置",
                    "未配置",
                    "未启用 COS 异地备份。本地电脑部署可选；腾讯云部署应启用并配置 Bucket、Region、Prefix、凭证模式和生命周期/保留策略。",
                    "blue"
            );
        }

        List<String> missing = new ArrayList<>();
        if (bucket == null || bucket.isBlank()) missing.add("bucket");
        if (region == null || region.isBlank()) missing.add("region");
        if (prefix == null || prefix.isBlank()) missing.add("prefix");
        if (credentialsMode == null || credentialsMode.isBlank()) missing.add("credentials-mode");
        if (lifecyclePolicy == null || lifecyclePolicy.isBlank()) missing.add("lifecycle-policy");

        if (!missing.isEmpty()) {
            return new CosBackupProbe(
                    "COS-BACKUP-INCOMPLETE",
                    "CONFIG_INCOMPLETE",
                    "配置待补",
                    "腾讯云 COS 异地备份",
                    "cos://配置不完整",
                    blankToReviewText(credentialsMode, "未配置"),
                    blankToReviewText(lifecyclePolicy, "未配置"),
                    "已启用 COS 异地备份，但缺少 " + String.join(", ", missing)
                            + "。运维中心只展示配置状态，不保存密钥、不执行上传或删除命令。",
                    "amber"
            );
        }

        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        String credentialNote = "temporary".equalsIgnoreCase(credentialsMode)
                ? "临时密钥/STS"
                : credentialsMode + "（需确认最小权限）";
        return new CosBackupProbe(
                "COS-BACKUP-READY",
                "READY",
                "配置完整",
                "腾讯云 COS 异地备份",
                "cos://" + bucket + "/" + normalizedPrefix,
                credentialsMode,
                lifecyclePolicy,
                "COS Bucket、Region、Prefix、凭证模式和生命周期/保留策略已配置；凭证模式：" + credentialNote
                        + "；生命周期：" + lifecyclePolicy
                        + "。页面只读展示 readiness，上传由受控脚本或 coscmd 执行。",
                "green"
        );
    }

    private OperationsCenterSnapshot.BackupStatus cosBackupStatus(CosBackupProbe cosBackup, Instant now) {
        return new OperationsCenterSnapshot.BackupStatus(
                cosBackup.code(),
                cosBackup.target(),
                cosBackup.label(),
                "由本地备份同步任务记录",
                DISPLAY_TIME.format(now.plusSeconds(backupRpoHours * 3600L)),
                cosBackup.storage(),
                "不适用",
                "沿用本地备份 SHA-256",
                cosBackup.detail(),
                cosBackup.tone()
        );
    }

    private List<OperationsCenterSnapshot.ErrorSummary> errorSummaries(Instant now, LogProbe logs) {
        if (!logs.errorSummaries().isEmpty()) {
            return logs.errorSummaries();
        }
        if ("READ_FILE".equals(logs.status())) {
            return List.of();
        }
        if ("MISSING".equals(logs.status()) || "READ_ERROR".equals(logs.status()) || "CONFIG_ERROR".equals(logs.status())) {
            return List.of(new OperationsCenterSnapshot.ErrorSummary(
                    "ERR-LOG-001",
                    "日志来源",
                    "P1",
                    1,
                    DISPLAY_TIME.format(now),
                    "运维负责人",
                    logs.detail(),
                    "amber"
            ));
        }

        return List.of(
                new OperationsCenterSnapshot.ErrorSummary(
                        "ERR-NVR-001",
                        "视频截取",
                        "P1",
                        1,
                        DISPLAY_TIME.format(now.minusSeconds(2_700)),
                        "现场实施负责人",
                        "检查 NVR 通道映射，失败允许人工上传视频片段。",
                        "amber"
                ),
                new OperationsCenterSnapshot.ErrorSummary(
                        "ERR-DB-001",
                        "数据库备份",
                        "P1",
                        backupRoot == null || backupRoot.isBlank() ? 1 : 0,
                        DISPLAY_TIME.format(now.minusSeconds(3_600)),
                        "运维负责人",
                        "配置备份目录、checksum、恢复演练记录和告警阈值。",
                        backupRoot == null || backupRoot.isBlank() ? "amber" : "green"
                ),
                new OperationsCenterSnapshot.ErrorSummary(
                        "ERR-AUTH-001",
                        "登录权限",
                        "P2",
                        1,
                        DISPLAY_TIME.format(now.minusSeconds(7_200)),
                        "平台负责人",
                        "当前只有轻量请求头权限守卫，仍需接入真实登录和菜单权限。",
                        "blue"
                )
        );
    }

    private LogProbe probeLogs(Instant now) {
        Path path = logFile == null || logFile.isBlank() ? null : Path.of(logFile);
        if (path == null) {
            return new LogProbe(
                    "REVIEW_SAMPLE",
                    "未配置 zhubao.operations.log-file，当前显示评审样例日志。",
                    "amber",
                    fallbackLogs(now),
                    List.of()
            );
        }
        if (!Files.exists(path)) {
            return new LogProbe(
                    "MISSING",
                    "日志文件不存在：" + path,
                    "red",
                    List.of(new OperationsCenterSnapshot.RecentLogEntry(
                            DISPLAY_TIME.format(now),
                            "WARN",
                            "operations-center",
                            "日志文件不存在：" + path,
                            "LOG-MISSING",
                            "amber"
                    )),
                    List.of()
            );
        }
        if (!Files.isRegularFile(path)) {
            return new LogProbe(
                    "CONFIG_ERROR",
                    "日志路径不是文件：" + path,
                    "red",
                    List.of(new OperationsCenterSnapshot.RecentLogEntry(
                            DISPLAY_TIME.format(now),
                            "WARN",
                            "operations-center",
                            "日志路径不是文件：" + path,
                            "LOG-NOT-FILE",
                            "amber"
                    )),
                    List.of()
            );
        }

        try {
            // POC 阶段只展示尾部少量行，生产环境应改为日志采集系统或高性能只读日志适配器。
            List<String> lines = Files.readAllLines(path);
            int start = Math.max(0, lines.size() - 8);
            List<OperationsCenterSnapshot.RecentLogEntry> entries = new ArrayList<>();
            for (int index = start; index < lines.size(); index++) {
                String line = lines.get(index);
                String level = detectLevel(line);
                entries.add(new OperationsCenterSnapshot.RecentLogEntry(
                        DISPLAY_TIME.format(now.minusSeconds((long) (lines.size() - index) * 60)),
                        level,
                        detectLogSource(line),
                        truncate(line, 220),
                        "LOG-" + (index + 1),
                        toneForLogLevel(level)
                ));
            }
            return new LogProbe(
                    "READ_FILE",
                    "已读取日志文件：" + path.getFileName(),
                    "green",
                    entries,
                    summarizeLogErrors(entries, now)
            );
        } catch (IOException error) {
            return new LogProbe(
                    "READ_ERROR",
                    "读取日志文件失败：" + truncate(error.getMessage(), 160),
                    "red",
                    List.of(new OperationsCenterSnapshot.RecentLogEntry(
                            DISPLAY_TIME.format(now),
                            "WARN",
                            "operations-center",
                            "读取日志文件失败：" + truncate(error.getMessage(), 160),
                            "LOG-READ-FAIL",
                            "amber"
                    )),
                    List.of()
            );
        }
    }

    private List<OperationsCenterSnapshot.RecentLogEntry> fallbackLogs(Instant now) {
        return List.of(
                new OperationsCenterSnapshot.RecentLogEntry(
                        DISPLAY_TIME.format(now.minusSeconds(420)),
                        "INFO",
                        "business-operation",
                        "生产报工写入库存台账，workOrder=ZD-20260706-021，status=WIP->SEMI_FINISHED。",
                        "TRACE-OPS-001",
                        "green"
                ),
                new OperationsCenterSnapshot.RecentLogEntry(
                        DISPLAY_TIME.format(now.minusSeconds(360)),
                        "WARN",
                        "hardware-video",
                        "NVR 视频截取任务失败，已转人工上传兜底，task=INT-VID-001。",
                        "TRACE-OPS-002",
                        "amber"
                ),
                new OperationsCenterSnapshot.RecentLogEntry(
                        DISPLAY_TIME.format(now.minusSeconds(240)),
                        "INFO",
                        "document-storage",
                        "附件元数据写入仓储，storage=local://evidence，repository=memory/JDBC 可切换。",
                        "TRACE-OPS-003",
                        "blue"
                ),
                new OperationsCenterSnapshot.RecentLogEntry(
                        DISPLAY_TIME.format(now.minusSeconds(120)),
                        "INFO",
                        "approval",
                        "损耗审批任务保持轻量闭环，formalPayroll=false，等待真实工资锁账边界。",
                        "TRACE-OPS-004",
                        "green"
                )
        );
    }

    private List<OperationsCenterSnapshot.ErrorSummary> summarizeLogErrors(
            List<OperationsCenterSnapshot.RecentLogEntry> entries,
            Instant now
    ) {
        Map<String, LogErrorAccumulator> grouped = new LinkedHashMap<>();
        for (OperationsCenterSnapshot.RecentLogEntry entry : entries) {
            if (!"ERROR".equals(entry.level()) && !"WARN".equals(entry.level())) {
                continue;
            }
            grouped.computeIfAbsent(entry.source(), LogErrorAccumulator::new).add(entry);
        }
        List<OperationsCenterSnapshot.ErrorSummary> summaries = new ArrayList<>();
        int index = 1;
        for (LogErrorAccumulator accumulator : grouped.values()) {
            summaries.add(new OperationsCenterSnapshot.ErrorSummary(
                    "LOG-" + String.format(Locale.ROOT, "%03d", index++),
                    accumulator.module(),
                    accumulator.hasError() ? "P1" : "P2",
                    accumulator.count(),
                    DISPLAY_TIME.format(now),
                    ownerForLogSource(accumulator.module()),
                    truncate(accumulator.latestMessage(), 180),
                    accumulator.hasError() ? "red" : "amber"
            ));
        }
        return summaries;
    }

    private List<OperationsCenterSnapshot.OperationChecklistItem> checklist(
            DatabaseProbe database,
            FrontendProbe frontend,
            PwaProbe pwa,
            LogProbe logs,
            CosBackupProbe cosBackup
    ) {
        return List.of(
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-001",
                        "后端 API 探活",
                        "已接入",
                        "/api/operations/center",
                        "平台负责人",
                        "green"
                ),
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-002",
                        "数据库连接探测",
                        database.status(),
                        database.detail(),
                        "后端负责人",
                        database.tone()
                ),
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-003",
                        "前端 URL 探活",
                        frontend.status(),
                        frontend.detail(),
                        "前端负责人",
                        frontend.tone()
                ),
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-004",
                        "PWA 静态资源探活",
                        pwa.status(),
                        pwa.detail(),
                        "前端负责人",
                        pwa.tone()
                ),
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-005",
                        "日志来源",
                        logs.status(),
                        logs.detail(),
                        "运维负责人",
                        logs.tone()
                ),
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-006",
                        "备份目录",
                        backupRoot == null || backupRoot.isBlank() ? "待配置" : "已配置",
                        blankToReviewText(backupRoot, "未配置 zhubao.operations.backup-root"),
                        "运维负责人",
                        backupRoot == null || backupRoot.isBlank() ? "amber" : "green"
                ),
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-007",
                        "腾讯云 COS 异地备份",
                        cosBackup.status(),
                        cosBackup.detail(),
                        "运维负责人",
                        cosBackup.tone()
                )
        );
    }

    private boolean backupToneAcceptable(OperationsCenterSnapshot.BackupStatus backup) {
        return "green".equals(backup.tone()) || "blue".equals(backup.tone());
    }

    private boolean isBackupFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".sql")
                || fileName.endsWith(".dump")
                || fileName.endsWith(".backup")
                || fileName.endsWith(".gz");
    }

    private Instant lastModifiedOrEpoch(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException error) {
            return Instant.EPOCH;
        }
    }

    private long sizeOrZero(Path path) {
        try {
            return Files.size(path);
        } catch (IOException error) {
            return 0L;
        }
    }

    private String readableSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f MiB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L) {
            return String.format(Locale.ROOT, "%.2f KiB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path);
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                byte[] buffer = new byte[8192];
                while (digestInput.read(buffer) != -1) {
                    // DigestInputStream updates the MessageDigest while bytes are consumed.
                }
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException error) {
            return "sha256:计算失败";
        }
    }

    private String detectLevel(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.contains("ERROR")) return "ERROR";
        if (upper.contains("WARN")) return "WARN";
        if (upper.contains("DEBUG")) return "DEBUG";
        return "INFO";
    }

    private String detectLogSource(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("nvr") || lower.contains("ffmpeg") || lower.contains("hardware") || lower.contains("video")) {
            return "视频截取";
        }
        if (lower.contains("backup") || lower.contains("mysqldump") || lower.contains("checksum") || lower.contains("备份")) {
            return "数据库备份";
        }
        if (lower.contains("auth") || lower.contains("permission") || lower.contains("login") || lower.contains("权限")) {
            return "登录权限";
        }
        if (lower.contains("jdbc") || lower.contains("mysql") || lower.contains("database") || lower.contains("sql")) {
            return "数据库连接";
        }
        if (lower.contains("document") || lower.contains("attachment") || lower.contains("storage")) {
            return "附件对象存储";
        }
        return "应用日志";
    }

    private String ownerForLogSource(String source) {
        return switch (source) {
            case "视频截取" -> "现场实施负责人";
            case "数据库备份", "数据库连接" -> "运维负责人";
            case "登录权限" -> "平台负责人";
            case "附件对象存储" -> "后端负责人";
            default -> "运维负责人";
        };
    }

    private String toneForLogLevel(String level) {
        if ("ERROR".equals(level)) return "red";
        if ("WARN".equals(level)) return "amber";
        if ("DEBUG".equals(level)) return "gray";
        return "green";
    }

    private String blankToReviewText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long toMiB(long bytes) {
        return bytes / 1024 / 1024;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private record DatabaseProbe(
            String status,
            String label,
            String detail,
            String endpoint,
            String tone
    ) {
    }

    private record FrontendProbe(
            String status,
            String detail,
            String endpoint,
            String tone
    ) {
    }

    private record PwaProbe(
            String status,
            String detail,
            String endpoint,
            String tone
    ) {
    }

    private record AssetProbe(
            int statusCode,
            String body
    ) {
        private boolean success() {
            return statusCode >= 200 && statusCode < 400;
        }
    }

    private record LogProbe(
            String status,
            String detail,
            String tone,
            List<OperationsCenterSnapshot.RecentLogEntry> entries,
            List<OperationsCenterSnapshot.ErrorSummary> errorSummaries
    ) {
        private LogProbe {
            entries = List.copyOf(entries);
            errorSummaries = List.copyOf(errorSummaries);
        }
    }

    private record CosBackupProbe(
            String code,
            String status,
            String label,
            String target,
            String storage,
            String credentialsMode,
            String lifecyclePolicy,
            String detail,
            String tone
    ) {
    }

    private static final class LogErrorAccumulator {
        private final String module;
        private int count;
        private boolean hasError;
        private String latestMessage = "";

        private LogErrorAccumulator(String module) {
            this.module = module;
        }

        private void add(OperationsCenterSnapshot.RecentLogEntry entry) {
            count++;
            hasError = hasError || "ERROR".equals(entry.level());
            latestMessage = entry.message();
        }

        private String module() {
            return module;
        }

        private int count() {
            return count;
        }

        private boolean hasError() {
            return hasError;
        }

        private String latestMessage() {
            return latestMessage;
        }
    }
}
