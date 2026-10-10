package com.zhubao.erp.operations.app;

import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.hardware.app.HardwareIntegrationService;
import com.zhubao.erp.hardware.domain.HardwarePocRecordRepository;
import com.zhubao.erp.operations.domain.OperationsCenterSnapshot;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsCenterServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsBackupFileSizeAndChecksumWhenBackupDirectoryIsConfigured() throws Exception {
        Path backup = tempDir.resolve("zhubao-20260707.sql");
        Files.writeString(backup, "select 1;\n");

        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> dataSources = mock(ObjectProvider.class);
        when(dataSources.getIfAvailable()).thenReturn(null);
        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                dataSources,
                "http://localhost:5173/",
                "",
                tempDir.toString(),
                "",
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();

        assertThat(snapshot.backupStatuses()).hasSize(3);
        OperationsCenterSnapshot.BackupStatus status = findBackup(snapshot, "DB-BACKUP-LATEST");
        assertThat(status.code()).isEqualTo("DB-BACKUP-LATEST");
        assertThat(status.status()).isEqualTo("已发现备份");
        assertThat(status.fileSize()).isEqualTo("10 B");
        assertThat(status.checksum()).startsWith("sha256:");
        assertThat(status.checksum()).hasSize(71);
        assertThat(status.detail()).contains("zhubao-20260707.sql").contains("10 B").contains("sha256:");

        OperationsCenterSnapshot.BackupStatus cos = findBackup(snapshot, "COS-BACKUP-DISABLED");
        assertThat(cos.status()).isEqualTo("未启用");
        assertThat(cos.tone()).isEqualTo("blue");

        OperationsCenterSnapshot.BackupStatus restoreDrill = findBackup(snapshot, "RESTORE-DRILL-NONE");
        assertThat(restoreDrill.status()).isEqualTo("未发现演练记录");
        assertThat(restoreDrill.detail()).contains("restore-").contains("verify-restore");
        assertThat(findChecklist(snapshot, "OPS-CHK-008").status()).isEqualTo("未发现演练记录");
    }

    @Test
    void reportsRestoreDrillMarkerWhenBackupDirectoryContainsVerificationRecord() throws Exception {
        Path backup = tempDir.resolve("zhubao-20260707.sql");
        Path restore = tempDir.resolve("verify-restore-20260707.log");
        Files.writeString(backup, "select 1;\n");
        Files.writeString(restore, "restore ok; tables=12\n");
        Files.writeString(tempDir.resolve("verify-restore-20260707.log.sha256"), "ignored sidecar\n");

        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                tempDir.toString(),
                "",
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();

        OperationsCenterSnapshot.BackupStatus restoreDrill = findBackup(snapshot, "RESTORE-DRILL-LATEST");
        assertThat(restoreDrill.status()).isEqualTo("已完成演练");
        assertThat(restoreDrill.target()).isEqualTo("恢复演练校验");
        assertThat(restoreDrill.storage()).contains("verify-restore-20260707.log");
        assertThat(restoreDrill.storage()).doesNotContain(".sha256");
        assertThat(restoreDrill.fileSize()).isEqualTo("22 B");
        assertThat(restoreDrill.checksum()).startsWith("sha256:");
        assertThat(restoreDrill.detail()).contains("最近恢复演练记录").contains("业务抽样");

        OperationsCenterSnapshot.OperationChecklistItem checklist = findChecklist(snapshot, "OPS-CHK-008");
        assertThat(checklist.status()).isEqualTo("已完成演练");
        assertThat(checklist.tone()).isEqualTo("green");
    }

    @Test
    void reportsStructuredRestoreDrillRecordWhenJsonVerificationRecordExists() throws Exception {
        Path backup = tempDir.resolve("zhubao-20260707.sql");
        Path restore = tempDir.resolve("verify-restore-20260707_090000.json");
        Files.writeString(backup, "select 1;\n");
        Files.writeString(restore, """
                {
                  "schemaVersion": "ops.restore-drill.v1",
                  "status": "passed",
                  "checkedAt": "2026-07-07T09:05:30+08:00",
                  "backupFile": "/backups/zhubao-20260707.sql",
                  "backupChecksum": "sha256:backup-sample",
                  "restoreDatabase": "zhubao_restore_check_20260707_090000",
                  "minimumTableCount": 8,
                  "restoredTableCount": 12,
                  "operator": "ops-user",
                  "durationSeconds": 36,
                  "keepDatabase": false,
                  "detail": "Non-destructive restore verification passed.",
                  "checks": [
                    {
                      "name": "backup_checksum",
                      "status": "passed",
                      "detail": "Backup SHA-256 recorded."
                    },
                    {
                      "name": "table_count",
                      "status": "passed",
                      "detail": "Restored 12 tables; minimum required 8."
                    }
                  ]
                }
                """);

        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                tempDir.toString(),
                "",
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();

        OperationsCenterSnapshot.BackupStatus restoreDrill = findBackup(snapshot, "RESTORE-DRILL-LATEST");
        assertThat(restoreDrill.storage()).contains("verify-restore-20260707_090000.json");
        assertThat(restoreDrill.detail()).contains("结构化恢复演练记录").contains("恢复表 12 张").contains("ops-user");
        assertThat(snapshot.restoreDrillRecords()).hasSize(1);
        OperationsCenterSnapshot.RestoreDrillRecord record = snapshot.restoreDrillRecords().get(0);
        assertThat(record.status()).isEqualTo("passed");
        assertThat(record.restoreDatabase()).isEqualTo("zhubao_restore_check_20260707_090000");
        assertThat(record.minimumTableCount()).isEqualTo(8);
        assertThat(record.restoredTableCount()).isEqualTo(12);
        assertThat(record.operator()).isEqualTo("ops-user");
        assertThat(record.backupChecksum()).isEqualTo("sha256:backup-sample");
        assertThat(record.tone()).isEqualTo("green");
        assertThat(record.checks())
                .extracting(OperationsCenterSnapshot.RestoreDrillCheck::name)
                .containsExactly("backup_checksum", "table_count");
    }

    @Test
    void reportsCosBackupAsDisabledWithoutBlockingLocalDeployments() {
        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                "",
                "",
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();
        OperationsCenterSnapshot.ServiceStatus cosService = findService(snapshot, "COS_BACKUP");
        OperationsCenterSnapshot.OperationChecklistItem cosChecklist = findChecklist(snapshot, "OPS-CHK-007");

        assertThat(cosService.status()).isEqualTo("DISABLED");
        assertThat(cosService.tone()).isEqualTo("blue");
        assertThat(cosService.detail()).contains("本地电脑部署可选").contains("腾讯云部署应启用");
        assertThat(cosChecklist.status()).isEqualTo("DISABLED");
        assertThat(cosChecklist.tone()).isEqualTo("blue");
    }

    @Test
    void reportsCosBackupConfigurationGapsWhenEnabledButRequiredFieldsAreMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhubao.operations.cos.enabled", "true")
                .withProperty("zhubao.operations.cos.bucket", "zhubao-prod-1250000000");

        OperationsCenterService service = new OperationsCenterService(
                environment,
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                "",
                "",
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();
        OperationsCenterSnapshot.ServiceStatus cosService = findService(snapshot, "COS_BACKUP");
        OperationsCenterSnapshot.BackupStatus cosBackup = findBackup(snapshot, "COS-BACKUP-INCOMPLETE");

        assertThat(cosService.status()).isEqualTo("CONFIG_INCOMPLETE");
        assertThat(cosService.tone()).isEqualTo("amber");
        assertThat(cosService.detail()).contains("region").contains("prefix").contains("credentials-mode").contains("lifecycle-policy");
        assertThat(cosBackup.storage()).isEqualTo("cos://配置不完整");
    }

    @Test
    void reportsCosBackupAsReadyWhenTencentCloudFieldsAreConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhubao.operations.cos.enabled", "true")
                .withProperty("zhubao.operations.cos.bucket", "zhubao-prod-1250000000")
                .withProperty("zhubao.operations.cos.region", "ap-guangzhou")
                .withProperty("zhubao.operations.cos.prefix", "prod/mysql/")
                .withProperty("zhubao.operations.cos.credentials-mode", "temporary")
                .withProperty("zhubao.operations.cos.lifecycle-policy", "180d-retention-versioning");

        OperationsCenterService service = new OperationsCenterService(
                environment,
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                "",
                "",
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();
        OperationsCenterSnapshot.ServiceStatus cosService = findService(snapshot, "COS_BACKUP");
        OperationsCenterSnapshot.BackupStatus cosBackup = findBackup(snapshot, "COS-BACKUP-READY");

        assertThat(cosService.status()).isEqualTo("READY");
        assertThat(cosService.tone()).isEqualTo("green");
        assertThat(cosService.endpoint()).isEqualTo("cos://zhubao-prod-1250000000/prod/mysql/");
        assertThat(cosService.detail()).contains("临时密钥/STS").contains("180d-retention-versioning");
        assertThat(cosBackup.status()).isEqualTo("配置完整");
        assertThat(cosBackup.checksum()).isEqualTo("沿用本地备份 SHA-256");
    }

    @Test
    void reportsFrontendOnlineWhenProbeUrlReturnsSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            String probeUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            OperationsCenterService service = new OperationsCenterService(
                    new MockEnvironment(),
                    noDataSourceProvider(),
                    "http://localhost/",
                    probeUrl,
                    "",
                    "",
                    800,
                    24
            );

            OperationsCenterSnapshot snapshot = service.snapshot();
            OperationsCenterSnapshot.ServiceStatus frontend = findService(snapshot, "FRONTEND");

            assertThat(frontend.status()).isEqualTo("ONLINE");
            assertThat(frontend.tone()).isEqualTo("green");
            assertThat(frontend.endpoint()).isEqualTo("http://localhost/");
            assertThat(frontend.detail()).contains("HTTP 200");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsFrontendProbeAsNotConfiguredWhenProbeUrlIsBlank() {
        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                "",
                "",
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();
        OperationsCenterSnapshot.ServiceStatus frontend = findService(snapshot, "FRONTEND");

        assertThat(frontend.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(frontend.tone()).isEqualTo("amber");
        assertThat(frontend.detail()).contains("未配置前端探活地址");
    }

    @Test
    void reportsPwaAssetsOnlineWhenManifestAndServiceWorkerAreReachable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/manifest.webmanifest", exchange -> {
            byte[] body = "{\"name\":\"来料加工 ERP\",\"start_url\":\"/\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/sw.js", exchange -> {
            byte[] body = "self.addEventListener('install',()=>{});self.addEventListener('activate',()=>{});".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String probeUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            OperationsCenterService service = new OperationsCenterService(
                    new MockEnvironment(),
                    noDataSourceProvider(),
                    "http://localhost/",
                    probeUrl,
                    "",
                    "",
                    800,
                    24
            );

            OperationsCenterSnapshot snapshot = service.snapshot();
            OperationsCenterSnapshot.ServiceStatus pwa = findService(snapshot, "PWA_ASSETS");

            assertThat(pwa.status()).isEqualTo("ONLINE");
            assertThat(pwa.tone()).isEqualTo("green");
            assertThat(pwa.detail()).contains("Manifest").contains("Service Worker");
            assertThat(pwa.endpoint()).contains("manifest.webmanifest").contains("sw.js");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsConfiguredLogFileAndAggregatesWarningsAndErrors() throws Exception {
        Path log = tempDir.resolve("zhubao-erp.log");
        Files.writeString(log, String.join("\n",
                "2026-07-07 08:00:00 INFO business-operation 生产报工写入库存台账",
                "2026-07-07 08:01:00 WARN hardware-video NVR 视频截取失败，已转人工上传",
                "2026-07-07 08:02:00 ERROR database-backup mysqldump failed checksum missing",
                "2026-07-07 08:03:00 INFO document-storage 附件元数据写入仓储"
        ));

        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                "",
                log.toString(),
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();

        assertThat(snapshot.recentLogs()).hasSize(4);
        assertThat(snapshot.recentLogs())
                .extracting(OperationsCenterSnapshot.RecentLogEntry::source)
                .contains("视频截取", "数据库备份", "附件对象存储");
        assertThat(snapshot.errorSummaries())
                .extracting(OperationsCenterSnapshot.ErrorSummary::module)
                .containsExactly("视频截取", "数据库备份");
        assertThat(snapshot.errorSummaries())
                .extracting(OperationsCenterSnapshot.ErrorSummary::severity)
                .containsExactly("P2", "P1");

        OperationsCenterSnapshot.OperationChecklistItem logChecklist = findChecklist(snapshot, "OPS-CHK-005");
        assertThat(logChecklist.status()).isEqualTo("READ_FILE");
        assertThat(logChecklist.tone()).isEqualTo("green");
        assertThat(logChecklist.evidence()).contains("zhubao-erp.log");
    }

    @Test
    void reportsHardwarePresenceAndStorageUsage() {
        @SuppressWarnings("unchecked")
        ObjectProvider<HardwareIntegrationService> hardwareServices = mock(ObjectProvider.class);
        DocumentManagementRepository documents = mock(DocumentManagementRepository.class);
        HardwarePocRecordRepository pocRecords = mock(HardwarePocRecordRepository.class);
        when(documents.attachments()).thenReturn(List.of());
        when(pocRecords.findAll()).thenReturn(List.of());
        when(hardwareServices.getIfAvailable()).thenReturn(new HardwareIntegrationService(pocRecords, documents));

        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                "",
                "",
                800,
                24,
                hardwareServices,
                null
        );

        OperationsCenterSnapshot snapshot = service.snapshot();

        assertThat(snapshot.storageVolumes())
                .extracting(OperationsCenterSnapshot.StorageVolume::code)
                .contains("SYSTEM_DISK", "EVIDENCE_STORE", "BACKUP_STORE");
        assertThat(snapshot.runtimeMetrics())
                .extracting(OperationsCenterSnapshot.RuntimeMetric::code)
                .contains("DISK");
        assertThat(snapshot.hardwareNodes())
                .extracting(OperationsCenterSnapshot.HardwarePresence::code)
                .contains("SCAN-PC-01", "CAM-UNPACK-01");
        assertThat(snapshot.hardwareNodes())
                .filteredOn(node -> "SCAN-PC-01".equals(node.code()))
                .singleElement()
                .satisfies(node -> assertThat(node.status()).isEqualTo("在线"));
        assertThat(findService(snapshot, "HARDWARE").detail()).contains("在线");
    }

    @Test
    void reportsOperationAlertsFromDegradedServicesBackupsAndLogErrors() throws Exception {
        Path log = tempDir.resolve("zhubao-erp.log");
        Files.writeString(log, String.join("\n",
                "2026-07-07 08:00:00 WARN hardware-video NVR 视频截取失败",
                "2026-07-07 08:01:00 ERROR database-backup mysqldump failed checksum missing"
        ));

        OperationsCenterService service = new OperationsCenterService(
                new MockEnvironment(),
                noDataSourceProvider(),
                "http://localhost:5173/",
                "",
                tempDir.toString(),
                log.toString(),
                800,
                24
        );

        OperationsCenterSnapshot snapshot = service.snapshot();

        assertThat(snapshot.summary().openAlerts()).isGreaterThanOrEqualTo(4);
        assertThat(snapshot.alerts())
                .extracting(OperationsCenterSnapshot.OperationAlert::source)
                .contains("SERVICE", "BACKUP", "LOG");
        assertThat(findAlert(snapshot, "OPS-ALERT-SVC-FRONTEND").nextAction()).contains("ZHUBAO_FRONTEND");
        assertThat(findAlert(snapshot, "OPS-ALERT-BACKUP-DB-BACKUP-EMPTY").nextAction()).contains("backup-mysql");
        assertThat(findAlert(snapshot, "OPS-ALERT-LOG-LOG-002").severity()).isEqualTo("P1");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DataSource> noDataSourceProvider() {
        ObjectProvider<DataSource> dataSources = mock(ObjectProvider.class);
        when(dataSources.getIfAvailable()).thenReturn(null);
        return dataSources;
    }

    private OperationsCenterSnapshot.ServiceStatus findService(OperationsCenterSnapshot snapshot, String code) {
        List<OperationsCenterSnapshot.ServiceStatus> services = snapshot.services();
        return services.stream()
                .filter(service -> code.equals(service.code()))
                .findFirst()
                .orElseThrow();
    }

    private OperationsCenterSnapshot.OperationChecklistItem findChecklist(OperationsCenterSnapshot snapshot, String code) {
        return snapshot.checklist().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }

    private OperationsCenterSnapshot.BackupStatus findBackup(OperationsCenterSnapshot snapshot, String code) {
        return snapshot.backupStatuses().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }

    private OperationsCenterSnapshot.OperationAlert findAlert(OperationsCenterSnapshot snapshot, String code) {
        return snapshot.alerts().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }
}
