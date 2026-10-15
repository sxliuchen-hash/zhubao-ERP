package com.zhubao.print;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/** 将服务端冻结的标签快照绘制为实际毫米尺寸的黑白图像。 */
public final class LabelRenderer {
    private LabelRenderer() {
    }

    public static byte[] render(String snapshot) throws Exception {
        Map<String, Object> data = SnapshotJson.parse(snapshot);
        int copies = integer(data, "copies", 1, 1, 20);
        if (copies < 1) throw new IllegalArgumentException("打印份数无效");
        return data.containsKey("fields") ? configured(data) : legacy(data);
    }

    private static byte[] configured(Map<String, Object> data) throws Exception {
        double widthMm = decimal(data, "widthMm", 40, 5, 150);
        double heightMm = decimal(data, "heightMm", 30, 5, 150);
        int dpi = integer(data, "dpi", 300, 100, 600);
        int width = pixels(widthMm, dpi);
        int height = pixels(heightMm, dpi);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = image.createGraphics();
        pen.setColor(Color.WHITE);
        pen.fillRect(0, 0, width, height);
        pen.setColor(Color.BLACK);
        pen.drawRect(0, 0, width - 1, height - 1);
        pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Map<String, Object> layout = object(data.get("layout"));
        String mode = string(layout, "mode");
        boolean compact = "COMPACT".equals(mode)
                || (!"STANDARD".equals(mode) && widthMm <= 40 && heightMm <= 30);
        int pad = pixels(compact ? 0.8 : 1.1, dpi);
        int gap = pixels(compact ? 0.8 : 1.2, dpi);
        int qrWidth = pixels(decimal(layout, "qrWidthMm", compact ? 17 : 18, 5, 100), dpi);
        qrWidth = Math.min(qrWidth, (int) (width * (compact ? 0.5 : 0.42)));
        int rightX = pad + qrWidth + gap;
        int rightWidth = Math.max(1, width - rightX - pad);
        double titlePt = decimal(layout, "titleFontPt", compact ? 7.5 : 7, 4, 36);
        double codePt = decimal(layout, "codeFontPt", compact ? 8.5 : 9, 4, 36);
        double fieldPt = decimal(layout, "fieldFontPt", compact ? 7.5 : 6.5, 4, 36);
        double emphasisPt = decimal(layout, "emphasisFontPt", compact ? 8.5 : 6.5, 4, 36);
        double footerPt = decimal(layout, "footerFontPt", 6, 4, 36);
        String code = string(data, "primaryCode");
        if (code.isBlank()) code = string(data, "businessCode");

        int y;
        int qrY;
        if (compact) {
            pen.setFont(font(titlePt, dpi, true));
            FontMetrics metrics = pen.getFontMetrics();
            String type = string(data, "typeLabel");
            int typeWidth = Math.min(metrics.stringWidth(type) + gap, width / 4);
            y = pad + metrics.getAscent();
            drawFit(pen, type, pad, y, typeWidth);
            pen.setFont(font(codePt, dpi, true));
            drawFit(pen, code, pad + typeWidth + gap, y, width - 2 * pad - typeWidth - gap);
            qrY = y + Math.max(metrics.getDescent(), pen.getFontMetrics().getDescent()) + gap;
            y = qrY;
        } else {
            qrY = pad + Math.max(0, (height - qrWidth) / 3);
            pen.setFont(font(titlePt, dpi, false));
            y = pad + pen.getFontMetrics().getAscent();
            drawFit(pen, string(data, "brandTitle") + "  " + string(data, "typeLabel"), rightX, y, rightWidth);
            pen.setFont(font(codePt, dpi, true));
            y += pen.getFontMetrics().getHeight() + pixels(0.5, dpi);
            drawFit(pen, code, rightX, y, rightWidth);
            y += pen.getFontMetrics().getDescent() + pixels(0.5, dpi);
        }
        drawQr(pen, string(data, "qr"), pad, qrY, qrWidth);
        int qrBottom = qrY + qrWidth;
        int fullWidth = Math.max(1, width - 2 * pad);
        List<?> fields = data.get("fields") instanceof List<?> list ? list : List.of();
        for (int index = 0; index < fields.size(); index++) {
            Map<String, Object> field = object(fields.get(index));
            String name = string(field, "name");
            String value = string(field, "value");
            if (name.isBlank() || value.isBlank()) continue;
            pen.setFont(font(index == 0 ? emphasisPt : fieldPt, dpi, true));
            FontMetrics metrics = pen.getFontMetrics();
            y += metrics.getAscent();
            if (y >= height - pad) break;
            if (compact) {
                y = drawAround(pen, name + "：" + value, y, rightX, rightWidth, pad, fullWidth,
                        qrBottom, height - pad, 4);
            } else {
                drawFit(pen, name + "：" + value, rightX, y, rightWidth);
            }
            y += metrics.getDescent() + pixels(compact ? 0.45 : 0.15, dpi);
        }
        String left = string(data, "footerLeft");
        String right = string(data, "footerRight");
        if (!left.isBlank() || !right.isBlank()) {
            pen.setFont(font(footerPt, dpi, false));
            int footerY = height - pad - pen.getFontMetrics().getDescent();
            if (footerY > y + gap) {
                int lineY = footerY - pen.getFontMetrics().getAscent() - gap / 2;
                pen.drawLine(pad, lineY, width - pad, lineY);
                drawFit(pen, left, pad, footerY, Math.max(1, width / 2 - pad - gap));
                drawFit(pen, right, width / 2, footerY, Math.max(1, width / 2 - pad));
            }
        }
        pen.dispose();
        return png(image);
    }

    private static byte[] legacy(Map<String, Object> data) throws Exception {
        int width = pixels(decimal(data, "widthMm", 60, 5, 150), 203);
        int height = pixels(decimal(data, "heightMm", 40, 5, 150), 203);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = image.createGraphics();
        pen.setColor(Color.WHITE);
        pen.fillRect(0, 0, width, height);
        pen.setColor(Color.BLACK);
        pen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        pen.drawString(string(data, "businessCode"), 12, 28);
        pen.drawString("版本 " + string(data, "version") + "  实例 " + string(data, "instance"), 12, 52);
        pen.drawString(string(data, "templateCode") + "  " + integer(data, "copies", 1, 1, 20) + " 份", 12, 76);
        drawQr(pen, string(data, "qr"), Math.max(0, width - 156), 16, 140);
        pen.dispose();
        return png(image);
    }

    private static void drawQr(Graphics2D pen, String content, int left, int top, int size) throws Exception {
        if (content.isBlank() || size < 21) return;
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.MARGIN, 0));
        pen.setColor(Color.WHITE);
        pen.fillRect(left, top, size, size);
        pen.setColor(Color.BLACK);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (matrix.get(column, row)) pen.fillRect(left + column, top + row, 1, 1);
            }
        }
    }

    private static void drawFit(Graphics2D pen, String text, int x, int baseline, int maxWidth) {
        if (text.isBlank() || maxWidth <= 0) return;
        FontMetrics metrics = pen.getFontMetrics();
        String fitted = text;
        while (!fitted.isEmpty() && metrics.stringWidth(fitted) > maxWidth) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        if (fitted.length() < text.length() && fitted.length() > 1) {
            fitted = fitted.substring(0, fitted.length() - 1) + "…";
        }
        pen.drawString(fitted, x, baseline);
    }

    /** 二维码右侧排不下的行改用整行，避免码下方留白。 */
    private static int drawAround(Graphics2D pen, String text, int baseline,
                                  int narrowX, int narrowWidth, int wideX, int wideWidth,
                                  int qrBottom, int bottom, int maxLines) {
        FontMetrics metrics = pen.getFontMetrics();
        String remaining = text;
        int y = baseline;
        for (int line = 0; line < maxLines && !remaining.isEmpty() && y <= bottom; line++) {
            boolean beside = y - metrics.getAscent() < qrBottom;
            int x = beside ? narrowX : wideX;
            int maxWidth = Math.max(1, beside ? narrowWidth : wideWidth);
            int count = remaining.length();
            while (count > 0 && metrics.stringWidth(remaining.substring(0, count)) > maxWidth) count--;
            if (count == 0) break;
            if (line == maxLines - 1 && count < remaining.length()) {
                drawFit(pen, remaining, x, y, maxWidth);
                break;
            }
            pen.drawString(remaining.substring(0, count), x, y);
            remaining = remaining.substring(count);
            if (!remaining.isEmpty()) y += metrics.getHeight();
        }
        return y;
    }

    private static Font font(double points, int dpi, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN,
                Math.max(1, (int) Math.round(points * dpi / 72d)));
    }

    private static int pixels(double mm, int dpi) {
        return Math.max(1, (int) Math.round(mm * dpi / 25.4d));
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    static String text(String json, String field) {
        Object value = find(SnapshotJson.parse(json), field);
        return value instanceof String string ? string : "";
    }

    private static Object find(Object node, String field) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey(field)) return map.get(field);
            for (Object child : map.values()) {
                Object found = find(child, field);
                if (found != null) return found;
            }
        } else if (node instanceof List<?> list) {
            for (Object child : list) {
                Object found = find(child, field);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String string ? string : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static int integer(Map<String, Object> data, String key, int fallback, int min, int max) {
        return (int) Math.round(decimal(data, key, fallback, min, max));
    }

    private static double decimal(Map<String, Object> data, String key, double fallback, double min, double max) {
        Object value = data.get(key);
        if (!(value instanceof Number number)) return fallback;
        double result = number.doubleValue();
        return Double.isFinite(result) && result >= min && result <= max ? result : fallback;
    }
}
