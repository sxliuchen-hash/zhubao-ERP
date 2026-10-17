package com.zhubao.erp.document.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.Locale;

/**
 * 附件两条上传入口共用的媒体类型与文件头校验。
 */
public final class AttachmentMediaPolicy {

    private AttachmentMediaPolicy() {
    }

    public static String canonicalType(String mediaType, boolean allowPdf) {
        String type = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(type)) {
            type = "image/jpeg";
        }
        if ("image/jpeg".equals(type) || "image/png".equals(type)
                || "video/mp4".equals(type) || "video/webm".equals(type)
                || allowPdf && "application/pdf".equals(type)) {
            return type;
        }
        throw new BusinessException(allowPdf ? "只接受 JPG、PNG、MP4、WebM、PDF" : "只接受 JPG、PNG、MP4、WebM");
    }

    public static boolean isVideo(String type) {
        return type.startsWith("video/");
    }

    public static boolean matchesHeader(String type, byte[] header, int length) {
        return switch (type) {
            case "image/jpeg" -> prefix(header, length, 0xff, 0xd8, 0xff);
            case "image/png" -> prefix(header, length, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "video/mp4" -> length >= 12 && header[4] == 'f' && header[5] == 't'
                    && header[6] == 'y' && header[7] == 'p';
            case "video/webm" -> prefix(header, length, 0x1a, 0x45, 0xdf, 0xa3);
            case "application/pdf" -> prefix(header, length, 0x25, 0x50, 0x44, 0x46, 0x2d);
            default -> false;
        };
    }

    private static boolean prefix(byte[] header, int length, int... expected) {
        if (length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((header[index] & 0xff) != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
