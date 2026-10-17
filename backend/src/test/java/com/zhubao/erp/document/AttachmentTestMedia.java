package com.zhubao.erp.document;

import java.nio.charset.StandardCharsets;

/**
 * 仅供附件测试使用的最小媒体文件头样本。
 */
public final class AttachmentTestMedia {

    private AttachmentTestMedia() {
    }

    public static byte[] jpeg(String payload) {
        return combine(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, payload);
    }

    public static byte[] mp4(String payload) {
        return combine(new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}, payload);
    }

    private static byte[] combine(byte[] header, String payload) {
        byte[] tail = payload.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[header.length + tail.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(tail, 0, result, header.length, tail.length);
        return result;
    }
}
