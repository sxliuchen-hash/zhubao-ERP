package com.zhubao.erp.operations.app;

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

        assertThat(snapshot.backupStatuses()).hasSize(1);
        OperationsCenterSnapshot.BackupStatus status = snapshot.backupStatuses().get(0);
        assertThat(status.code()).isEqualTo("DB-BACKUP-LATEST");
        assertThat(status.status()).isEqualTo("已发现备份");
        assertThat(status.fileSize()).isEqualTo("10 B");
        assertThat(status.checksum()).startsWith("sha256:");
        assertThat(status.checksum()).hasSize(71);
        assertThat(status.detail()).contains("zhubao-20260707.sql").contains("10 B").contains("sha256:");
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
}
