package com.zhubao.erp.document.domain;

import java.io.IOException;
import java.io.OutputStream;

public record AttachmentStreamContent(
        String code,
        String fileName,
        String mediaType,
        long size,
        byte[] header,
        RangeWriter writer
) {
    public void write(long offset, long length, OutputStream output) throws IOException {
        writer.write(offset, length, output);
    }

    @FunctionalInterface
    public interface RangeWriter {
        void write(long offset, long length, OutputStream output) throws IOException;
    }
}
