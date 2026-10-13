package com.zhubao.print;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * 把服务端标签快照画成固定毫米尺寸的黑白标签。不接收客户端打印指令。
 */
public final class LabelRenderer {

    private LabelRenderer() {
    }

    public static byte[] render(String snapshot) throws Exception {
        int widthMm = number(snapshot, "widthMm", 60);
        int heightMm = number(snapshot, "heightMm", 40);
        int copies = number(snapshot, "copies", 1);
        if (copies < 1) {
            throw new IllegalArgumentException("打印份数无效");
        }
        int dpi = 203;
        int width = Math.max(1, (int) Math.round(widthMm * dpi / 25.4d));
        int height = Math.max(1, (int) Math.round(heightMm * dpi / 25.4d));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setColor(Color.WHITE);
        canvas.fillRect(0, 0, width, height);
        canvas.setColor(Color.BLACK);
        canvas.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        int y = 28;
        canvas.drawString(text(snapshot, "businessCode"), 12, y);
        y += 24;
        canvas.drawString("版本 " + text(snapshot, "version") + "  实例 " + text(snapshot, "instance"), 12, y);
        y += 24;
        canvas.drawString(text(snapshot, "templateCode") + "  " + copies + " 份", 12, y);
        String qr = text(snapshot, "qr");
        if (!qr.isBlank()) {
            BitMatrix matrix = new QRCodeWriter().encode(qr, BarcodeFormat.QR_CODE, 140, 140);
            int left = width - 156;
            int top = 16;
            for (int row = 0; row < matrix.getHeight(); row++) {
                for (int column = 0; column < matrix.getWidth(); column++) {
                    if (matrix.get(column, row)) {
                        canvas.fillRect(left + column, top + row, 1, 1);
                    }
                }
            }
        }
        canvas.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    static String text(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        for (int index = start + needle.length(); index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '\\' && index + 1 < json.length()) {
                value.append(json.charAt(++index));
                continue;
            }
            if (current == '"') {
                return value.toString();
            }
            value.append(current);
        }
        return value.toString();
    }

    private static int number(String json, String field, int fallback) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int from = start + needle.length();
        int end = from;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == from) {
            return fallback;
        }
        return Integer.parseInt(json.substring(from, end));
    }
}