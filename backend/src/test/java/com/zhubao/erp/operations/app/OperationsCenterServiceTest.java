package com.zhubao.erp.operations.app;

import com.zhubao.erp.operations.domain.OperationsCenterSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

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
                tempDir.toString(),
                "",
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
}
