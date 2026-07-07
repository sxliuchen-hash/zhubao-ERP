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
}
