package com.zhubao.erp.document.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.domain.StoredAttachmentObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class LocalAttachmentObjectStorage implements AttachmentObjectStorage {

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
        String safeMediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType;
        String storageKey = storageKey(documentCode, attachmentCode, fileName);
        Path target = resolveStorageKey(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content.clone());
        } catch (IOException exception) {
            throw new BusinessException("附件写入本地存储失败：" + exception.getMessage());
        }
        return new StoredAttachmentObject(
                storageKey,
                "local://" + storageKey,
                safeMediaType,
                content.length,
                "sha256:" + sha256(content)
        );
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
        String safeMediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType;
        String storageKey = storageKey(documentCode, attachmentCode, fileName);
        Path target = resolveStorageKey(storageKey);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException exception) {
            throw new BusinessException("附件写入本地存储失败：" + exception.getMessage());
        }
        MessageDigest digest = sha256Digest();
        long written = 0;
        try (InputStream source = input; OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) >= 0) {
                written += read;
                if (written > maxBytes) {
                    throw new BusinessException("文件超过大小限制");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (BusinessException exception) {
            deleteQuietly(target);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new BusinessException("附件写入本地存储失败：" + exception.getMessage());
        }
        if (written == 0) {
            deleteQuietly(target);
            throw new BusinessException("附件内容不能为空");
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

    private String sha256(byte[] content) {
        MessageDigest digest = sha256Digest();
        return HexFormat.of().formatHex(digest.digest(content));
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
