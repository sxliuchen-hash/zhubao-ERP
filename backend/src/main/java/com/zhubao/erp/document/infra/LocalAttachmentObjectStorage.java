package com.zhubao.erp.document.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.domain.AttachmentMediaPolicy;
import com.zhubao.erp.document.domain.StoredAttachmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class LocalAttachmentObjectStorage implements AttachmentObjectStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalAttachmentObjectStorage.class);
    private final AttachmentStorageProperties properties;

    public LocalAttachmentObjectStorage(AttachmentStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredAttachmentObject put(
            String documentCode,
            String attachmentCode,
            String fileName,
            String mediaType,
            byte[] content
    ) {
        if (content == null || content.length == 0) {
            throw new BusinessException("附件内容不能为空");
        }
        return putStream(documentCode, attachmentCode, fileName, mediaType,
                new ByteArrayInputStream(content), content.length, content.length);
    }

    @Override
    public StoredAttachmentObject putStream(
            String documentCode,
            String attachmentCode,
            String fileName,
            String mediaType,
            InputStream input,
            long declaredSize,
            long maxBytes
    ) {
        if (input == null) {
            throw new BusinessException("附件内容不能为空");
        }
        if (declaredSize > maxBytes || maxBytes <= 0) {
            throw new BusinessException("文件超过大小限制");
        }
        String safeMediaType = AttachmentMediaPolicy.canonicalType(mediaType, true);
        String storageKey = storageKey(documentCode, attachmentCode, fileName);
        Path target = resolveStorageKey(storageKey);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException exception) {
            throw new BusinessException("附件写入本地存储失败：" + exception.getMessage());
        }
        MessageDigest digest = sha256Digest();
        byte[] header = new byte[12];
        int headerLength = 0;
        long written = 0;
        Path pending;
        try {
            pending = Files.createTempFile(target.getParent(), ".attachment-", ".pending");
        } catch (IOException exception) {
            throw new BusinessException("附件写入本地存储失败：" + exception.getMessage());
        }
        try {
            try (InputStream source = input; OutputStream output = Files.newOutputStream(pending)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = source.read(buffer)) >= 0) {
                    written += read;
                    if (written > maxBytes) {
                        throw new BusinessException("文件超过大小限制");
                    }
                    int captured = Math.min(read, header.length - headerLength);
                    System.arraycopy(buffer, 0, header, headerLength, captured);
                    headerLength += captured;
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (written == 0) {
                throw new BusinessException("附件内容不能为空");
            }
            if (!AttachmentMediaPolicy.matchesHeader(safeMediaType, header, headerLength)) {
                throw new BusinessException("文件内容与声明类型不符");
            }
            if (Files.exists(target)) {
                throw new BusinessException("附件存储对象已存在：" + storageKey);
            }
            try {
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new BusinessException("附件存储不支持原子发布");
            }
        } catch (IOException exception) {
            throw new BusinessException("附件写入本地存储失败：" + exception.getMessage());
        } finally {
            try {
                Files.deleteIfExists(pending);
            } catch (IOException ignored) {
                // 未发布的临时对象留待存储对账报告，不覆盖原始失败。
            }
        }
        if ("image/jpeg".equals(safeMediaType) || "image/png".equals(safeMediaType)) {
            try {
                ensureImageDerivative(storageKey, "thumbnail");
                ensureImageDerivative(storageKey, "preview");
            } catch (RuntimeException exception) {
                LOGGER.warn("附件 {} 的派生图可按需重建：{}", attachmentCode, exception.getMessage());
            }
        }
        return new StoredAttachmentObject(
                storageKey,
                "local://" + storageKey,
                safeMediaType,
                written,
                "sha256:" + HexFormat.of().formatHex(digest.digest())
        );
    }

    @Override
    public byte[] get(String storageKey) {
        Path target = resolveStorageKey(storageKey);
        if (!Files.exists(target)) {
            throw new BusinessException("附件存储对象不存在：" + storageKey);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new BusinessException("附件读取本地存储失败：" + exception.getMessage());
        }
    }

    @Override
    public long size(String storageKey) {
        Path target = existingObject(storageKey);
        try {
            return Files.size(target);
        } catch (IOException exception) {
            throw new BusinessException("附件读取本地存储失败：" + exception.getMessage());
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path target = existingObject(storageKey);
        try {
            return Files.newInputStream(target);
        } catch (IOException exception) {
            throw new BusinessException("附件读取本地存储失败：" + exception.getMessage());
        }
    }

    @Override
    public synchronized String ensureImageDerivative(String storageKey, String resolution) {
        int maxEdge = switch (resolution) {
            case "thumbnail" -> 320;
            case "preview" -> 1280;
            default -> throw new BusinessException("图片派生图清晰度不支持");
        };
        Path source = existingObject(storageKey);
        String key = derivativeStorageKey(storageKey, resolution);
        Path target = resolveStorageKey(key);
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return key;
        }
        Path pending = null;
        try {
            Files.createDirectories(target.getParent());
            pending = Files.createTempFile(target.getParent(), ".derivative-", ".pending");
            ImageDerivativeGenerator.write(source, pending, maxEdge);
            try {
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new BusinessException("派生图存储不支持原子发布");
            }
            return key;
        } catch (IOException exception) {
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return key;
            throw new BusinessException("图片派生图写入失败：" + exception.getMessage());
        } finally {
            if (pending != null) {
                try {
                    Files.deleteIfExists(pending);
                } catch (IOException exception) {
                    LOGGER.warn("派生图临时文件保留待核对：{}", pending);
                }
            }
        }
    }

    private String derivativeStorageKey(String storageKey, String resolution) {
        String digest = HexFormat.of().formatHex(sha256Digest().digest(storageKey.getBytes(StandardCharsets.UTF_8)));
        return "derived-images/" + digest + "/" + resolution + ".jpg";
    }

    private Path existingObject(String storageKey) {
        Path target = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException("附件存储对象不存在：" + storageKey);
        }
        return target;
    }

    @Override
    public void delete(String storageKey) {
        deleteQuietly(resolveStorageKey(storageKey));
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new BusinessException("附件删除失败：" + exception.getMessage());
        }
    }

    private String storageKey(String documentCode, String attachmentCode, String fileName) {
        return "evidence/"
                + safeSegment(documentCode)
                + "/"
                + safeSegment(attachmentCode)
                + "/"
                + safeSegment(fileName == null || fileName.isBlank() ? "attachment.bin" : fileName);
    }

    private Path resolveStorageKey(String storageKey) {
        Path root = properties.localRootPath().toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法附件存储路径：" + storageKey);
        }
        return target;
    }

    private String safeSegment(String value) {
        String segment = value == null ? "unknown" : value.trim();
        if (segment.isBlank()) {
            return "unknown";
        }
        return segment
                .replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_")
                .replaceAll("[\\p{Cntrl}]", "_");
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
