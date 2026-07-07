package com.zhubao.erp.operations.app;

import com.zhubao.erp.operations.domain.OperationsCenterSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final String backupRoot;
    private final String logFile;
    private final int backupRpoHours;

    public OperationsCenterService(
            Environment environment,
            ObjectProvider<DataSource> dataSources,
            @Value("${zhubao.operations.frontend-url:http://localhost:5173/}") String frontendUrl,
            @Value("${zhubao.operations.backup-root:}") String backupRoot,
            @Value("${zhubao.operations.log-file:}") String logFile,
            @Value("${zhubao.operations.backup-rpo-hours:24}") int backupRpoHours
    ) {
        this.environment = environment;
        this.dataSources = dataSources;
        this.frontendUrl = frontendUrl;
        this.backupRoot = backupRoot;
        this.logFile = logFile;
        this.backupRpoHours = backupRpoHours;
    }

    public OperationsCenterSnapshot snapshot() {
        Instant now = Instant.now();
        DatabaseProbe database = probeDatabase(now);
        List<OperationsCenterSnapshot.BackupStatus> backups = probeBackups(now);
        List<OperationsCenterSnapshot.ServiceStatus> services = serviceStatuses(database, backups, now);
        List<OperationsCenterSnapshot.ErrorSummary> errors = errorSummaries(now);

        long degradedServices = services.stream().filter(service -> !"green".equals(service.tone())).count();
        int criticalErrors = (int) errors.stream().filter(error -> "red".equals(error.tone())).count();
        String backupStatus = backups.stream().allMatch(backup -> "green".equals(backup.tone())) ? "正常" : "需配置";
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
                recentLogs(now),
                backups,
                checklist(database)
        );
    }

    private List<OperationsCenterSnapshot.ServiceStatus> serviceStatuses(
            DatabaseProbe database,
            List<OperationsCenterSnapshot.BackupStatus> backups,
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
                "REVIEW",
                frontendUrl,
                "当前按配置 URL 展示前端状态，生产环境需接入探活和静态资源版本校验。",
                "前端负责人",
                checkedAt,
                "blue"
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
        String backupTone = backups.stream().allMatch(backup -> "green".equals(backup.tone())) ? "green" : "amber";
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

    private List<OperationsCenterSnapshot.BackupStatus> probeBackups(Instant now) {
        if (backupRoot == null || backupRoot.isBlank()) {
            return List.of(new OperationsCenterSnapshot.BackupStatus(
                    "DB-BACKUP-POC",
                    "MySQL 逻辑备份",
                    "未接入真实目录",
                    "评审样例：2026-07-06 23:30:00",
                    DISPLAY_TIME.format(now.plusSeconds(backupRpoHours * 3600L)),
                    "zhubao.operations.backup-root 未配置",
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
                        "目录存在但没有 .sql/.dump/.backup/.gz 文件。",
                        "red"
                ));
            }
            Path latestFile = latest.get();
            Instant lastModified = lastModifiedOrEpoch(latestFile);
            return List.of(new OperationsCenterSnapshot.BackupStatus(
                    "DB-BACKUP-LATEST",
                    "MySQL 逻辑备份",
                    "已发现备份",
                    DISPLAY_TIME.format(lastModified),
                    DISPLAY_TIME.format(lastModified.plusSeconds(backupRpoHours * 3600L)),
                    latestFile.toString(),
                    "最近备份文件 " + latestFile.getFileName(),
                    "green"
            ));
        } catch (IOException error) {
            return List.of(new OperationsCenterSnapshot.BackupStatus(
                    "DB-BACKUP-ERROR",
                    "MySQL 逻辑备份",
                    "读取失败",
                    "无",
                    DISPLAY_TIME.format(now.plusSeconds(backupRpoHours * 3600L)),
                    root.toString(),
                    error.getClass().getSimpleName() + ": " + truncate(error.getMessage(), 160),
                    "red"
            ));
        }
    }

    private List<OperationsCenterSnapshot.ErrorSummary> errorSummaries(Instant now) {
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

    private List<OperationsCenterSnapshot.RecentLogEntry> recentLogs(Instant now) {
        Path path = logFile == null || logFile.isBlank() ? null : Path.of(logFile);
        if (path != null && Files.exists(path)) {
            try {
                // POC 阶段只读取末尾少量行，生产环境应改为日志采集系统或只读日志文件适配器。
                List<String> lines = Files.readAllLines(path);
                int start = Math.max(0, lines.size() - 6);
                List<OperationsCenterSnapshot.RecentLogEntry> entries = new ArrayList<>();
                for (int index = start; index < lines.size(); index++) {
                    String line = lines.get(index);
                    entries.add(new OperationsCenterSnapshot.RecentLogEntry(
                            DISPLAY_TIME.format(now.minusSeconds((long) (lines.size() - index) * 60)),
                            detectLevel(line),
                            path.getFileName().toString(),
                            truncate(line, 220),
                            "LOG-" + (index + 1),
                            toneForLogLevel(detectLevel(line))
                    ));
                }
                return entries;
            } catch (IOException error) {
                return List.of(new OperationsCenterSnapshot.RecentLogEntry(
                        DISPLAY_TIME.format(now),
                        "WARN",
                        "operations-center",
                        "读取日志文件失败：" + truncate(error.getMessage(), 160),
                        "LOG-READ-FAIL",
                        "amber"
                ));
            }
        }

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

    private List<OperationsCenterSnapshot.OperationChecklistItem> checklist(DatabaseProbe database) {
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
                        "日志来源",
                        logFile == null || logFile.isBlank() ? "评审样例" : "读取文件",
                        blankToReviewText(logFile, "未配置 zhubao.operations.log-file"),
                        "运维负责人",
                        logFile == null || logFile.isBlank() ? "amber" : "green"
                ),
                new OperationsCenterSnapshot.OperationChecklistItem(
                        "OPS-CHK-004",
                        "备份目录",
                        backupRoot == null || backupRoot.isBlank() ? "待配置" : "已配置",
                        blankToReviewText(backupRoot, "未配置 zhubao.operations.backup-root"),
                        "运维负责人",
                        backupRoot == null || backupRoot.isBlank() ? "amber" : "green"
                )
        );
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

    private String detectLevel(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.contains("ERROR")) return "ERROR";
        if (upper.contains("WARN")) return "WARN";
        if (upper.contains("DEBUG")) return "DEBUG";
        return "INFO";
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
}
