package com.zhubao.erp.document.infra;

import com.zhubao.erp.common.error.BusinessException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/** 仅从原件重建 JPEG 派生图，原件字节和方向标记均不改动。 */
final class ImageDerivativeGenerator {

    private static final long MAX_PIXELS = 24_000_000L;
    private static final int MAX_EXIF_BYTES = 131_072;

    private ImageDerivativeGenerator() {
    }

    static void write(Path source, Path target, int maxEdge) {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) {
                throw new BusinessException("无法读取图片原件");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException("图片原件无法解码");
            }
            ImageReader reader = readers.next();
            BufferedImage original;
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new BusinessException("图片尺寸超过派生图处理上限");
                }
                original = reader.read(0);
            } finally {
                reader.dispose();
            }
            if (original == null) {
                throw new BusinessException("图片原件无法解码");
            }
            int orientation = exifOrientation(source);
            int sourceWidth = original.getWidth();
            int sourceHeight = original.getHeight();
            boolean swap = orientation >= 5 && orientation <= 8;
            int orientedWidth = swap ? sourceHeight : sourceWidth;
            int orientedHeight = swap ? sourceWidth : sourceHeight;
            double scale = Math.min(1.0, (double) maxEdge / Math.max(orientedWidth, orientedHeight));
            int outputWidth = Math.max(1, (int) Math.round(orientedWidth * scale));
            int outputHeight = Math.max(1, (int) Math.round(orientedHeight * scale));
            BufferedImage output = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, outputWidth, outputHeight);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.scale((double) outputWidth / orientedWidth, (double) outputHeight / orientedHeight);
                graphics.transform(orientationTransform(orientation, sourceWidth, sourceHeight));
                graphics.drawImage(original, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            writeJpeg(output, target);
        } catch (IOException exception) {
            throw new BusinessException("图片派生图生成失败：" + exception.getMessage());
        }
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new BusinessException("运行环境不支持 JPEG 编码");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0.78f);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private static AffineTransform orientationTransform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    private static int exifOrientation(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            byte[] bytes = input.readNBytes(MAX_EXIF_BYTES);
            if (bytes.length < 4 || unsigned(bytes, 0) != 0xff || unsigned(bytes, 1) != 0xd8) {
                return 1;
            }
            int position = 2;
            while (position + 4 <= bytes.length) {
                if (unsigned(bytes, position) != 0xff) break;
                int marker = unsigned(bytes, position + 1);
                if (marker == 0xda || marker == 0xd9) break;
                int segmentLength = (unsigned(bytes, position + 2) << 8) | unsigned(bytes, position + 3);
                if (segmentLength < 2 || position + 2 + segmentLength > bytes.length) break;
                if (marker == 0xe1) {
                    int orientation = orientationInSegment(bytes, position + 4, segmentLength - 2);
                    if (orientation != 1) return orientation;
                }
                position += 2 + segmentLength;
            }
            return 1;
        }
    }

    private static int orientationInSegment(byte[] bytes, int start, int length) {
        if (length < 14 || start + length > bytes.length
                || bytes[start] != 'E' || bytes[start + 1] != 'x' || bytes[start + 2] != 'i'
                || bytes[start + 3] != 'f' || bytes[start + 4] != 0 || bytes[start + 5] != 0) {
            return 1;
        }
        int tiff = start + 6;
        boolean little = bytes[tiff] == 'I' && bytes[tiff + 1] == 'I';
        boolean big = bytes[tiff] == 'M' && bytes[tiff + 1] == 'M';
        if (!little && !big) return 1;
        int end = start + length;
        if (shortAt(bytes, tiff + 2, little) != 42) return 1;
        long offset = intAt(bytes, tiff + 4, little);
        if (offset > end - tiff - 2) return 1;
        int directory = tiff + (int) offset;
        int count = shortAt(bytes, directory, little);
        for (int index = 0; index < count; index++) {
            int entry = directory + 2 + index * 12;
            if (entry + 12 > end) return 1;
            if (shortAt(bytes, entry, little) == 0x0112
                    && shortAt(bytes, entry + 2, little) == 3
                    && intAt(bytes, entry + 4, little) == 1) {
                int orientation = shortAt(bytes, entry + 8, little);
                return orientation >= 1 && orientation <= 8 ? orientation : 1;
            }
        }
        return 1;
    }

    private static int shortAt(byte[] bytes, int offset, boolean little) {
        return little ? unsigned(bytes, offset) | unsigned(bytes, offset + 1) << 8
                : unsigned(bytes, offset) << 8 | unsigned(bytes, offset + 1);
    }

    private static long intAt(byte[] bytes, int offset, boolean little) {
        return little ? ((long) shortAt(bytes, offset, true) | (long) shortAt(bytes, offset + 2, true) << 16)
                : ((long) shortAt(bytes, offset, false) << 16 | shortAt(bytes, offset + 2, false));
    }

    private static int unsigned(byte[] bytes, int offset) {
        return bytes[offset] & 0xff;
    }
}
