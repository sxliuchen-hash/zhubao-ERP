package com.zhubao.erp.document.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.AttachmentObjectStorage;
import com.zhubao.erp.document.domain.StoredAttachmentObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
