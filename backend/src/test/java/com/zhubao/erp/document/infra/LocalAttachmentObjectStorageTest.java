package com.zhubao.erp.document.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
}
