package com.zhubao.erp.document.infra;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.zhubao.erp.document.AttachmentTestMedia.jpeg;

class LocalAttachmentObjectStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAttachmentBySafeObjectKeyAndReadsItBack() throws IOException {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(properties);

        byte[] content = jpeg("handoff evidence");
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
        assertThat(storage.size(stored.storageKey())).isEqualTo(content.length);
        try (InputStream input = storage.open(stored.storageKey())) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
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

    @Test
    void rejectsMismatchedContentAndAcceptsPdfHeader() throws IOException {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(properties);

        assertThatThrownBy(() -> storage.put("DOC-1", "ATT-BAD", "fake.jpg", "image/jpeg", "plain".getBytes()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容与声明类型不符");
        var pdf = storage.put("DOC-1", "ATT-PDF", "invoice.pdf", "application/pdf", "%PDF-1.7".getBytes());
        assertThat(storage.get(pdf.storageKey())).isEqualTo("%PDF-1.7".getBytes());
        try (var paths = Files.walk(tempDir)) {
            assertThat(paths.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    @Test
    void createsAndRebuildsImageDerivativesWithoutChangingOriginal() throws IOException {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(properties);
        BufferedImage source = new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 1600, 800);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        byte[] original = bytes.toByteArray();

        var stored = storage.put("DOC-1", "ATT-PNG", "photo.png", "image/png", original);
        String thumbnailKey = storage.ensureImageDerivative(stored.storageKey(), "thumbnail");
        String previewKey = storage.ensureImageDerivative(stored.storageKey(), "preview");
        assertThat(ImageIO.read(tempDir.resolve(thumbnailKey).toFile()).getWidth()).isEqualTo(320);
        assertThat(ImageIO.read(tempDir.resolve(previewKey).toFile()).getWidth()).isEqualTo(1280);
        Files.delete(tempDir.resolve(thumbnailKey));
        assertThat(storage.ensureImageDerivative(stored.storageKey(), "thumbnail")).isEqualTo(thumbnailKey);
        assertThat(Files.isRegularFile(tempDir.resolve(thumbnailKey))).isTrue();
        assertThat(storage.get(stored.storageKey())).isEqualTo(original);
    }

    @Test
    void appliesExifOrientationOnlyToDerivedImage() throws IOException {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        LocalAttachmentObjectStorage storage = new LocalAttachmentObjectStorage(properties);
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.RED.getRGB());
        source.setRGB(1, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", plain);
        byte[] jpeg = plain.toByteArray();
        byte[] exif = new byte[]{
                (byte) 0xff, (byte) 0xe1, 0, 34,
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0, 18, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0,
                0, 0, 0, 0
        };
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        withExif.write(exif);
        withExif.write(jpeg, 2, jpeg.length - 2);
        byte[] original = withExif.toByteArray();

        var stored = storage.put("DOC-1", "ATT-EXIF", "photo.jpg", "image/jpeg", original);
        var thumbnail = ImageIO.read(tempDir.resolve(storage.ensureImageDerivative(stored.storageKey(), "thumbnail")).toFile());
        assertThat(thumbnail.getWidth()).isEqualTo(1);
        assertThat(thumbnail.getHeight()).isEqualTo(2);
        assertThat(storage.get(stored.storageKey())).isEqualTo(original);
    }
}
