package com.zhubao.erp.document.infra;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAttachmentObjectStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAttachmentBySafeObjectKeyAndReadsItBack() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(properties);

        byte[] content = "handoff evidence".getBytes();
        var stored = storage.put(
                "DOC/../INBOUND",
                "ATT-UP-001",
                "..\\weight-photo.jpg",
                "image/jpeg",
                content
        );

        assertThat(stored.storageKey()).startsWith("evidence/");
        assertThat(stored.storageKey()).doesNotContain("..");
        assertThat(stored.storageLocation()).startsWith("local://evidence/");
        assertThat(stored.checksum()).startsWith("sha256:");
        assertThat(Files.exists(tempDir.resolve(stored.storageKey()))).isTrue();
        assertThat(storage.get(stored.storageKey())).isEqualTo(content);
    }

    @Test
    void interruptedStreamLeavesNoPublishedOrPendingObject() throws IOException {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(properties);
        InputStream interrupted = new InputStream() {
            private boolean first = true;

            @Override
            public int read() throws IOException {
                if (first) {
                    first = false;
                    return 'x';
                }
                throw new IOException("连接中断");
            }
        };

        assertThatThrownBy(() -> storage.putStream("DOC-1", "ATT-1", "photo.jpg",
                "image/jpeg", interrupted, -1, 1024))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("写入本地存储失败");
        try (var paths = Files.walk(tempDir)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }
}
