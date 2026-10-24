package com.zhubao.print;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelRendererTest {

    @Test
    void 圆章占用独立区域且空间不足明确拒绝() throws Exception {
        String common = "{\"copies\":1,\"widthMm\":40,\"heightMm\":30,\"dpi\":300,\"fields\":[],\"qr\":\"JG|mother|MD-1|0|LI1\",\"storageSources\":[{\"sourceLabel\":\"明细1\",\"marks\":[\"冷\",\"真\"],\"instruction\":\"";
        byte[] rendered = LabelRenderer.render(common + "\"}]}");
        java.nio.file.Files.write(java.nio.file.Path.of("target/storage-label-java.png"), rendered);
        var image = ImageIO.read(new ByteArrayInputStream(rendered));
        assertTrue(hasBlackPixelIn(image, 100, 300, 300, 340));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> LabelRenderer.render(common + "测".repeat(200) + "\"}]}"));
    }

    @Test
    void rendersMillimeterLabelWithQrPayload() throws Exception {
        String snapshot = "{\"kind\":\"LABEL\",\"businessCode\":\"MD-1\",\"templateCode\":\"FLOW\",\"copies\":2,"
                + "\"widthMm\":60,\"heightMm\":40,\"version\":\"V1\",\"instance\":\"LI1\","
                + "\"qr\":\"JG|mother|MD-1|1|LI1\",\"lines\":[\"单号:MD-1\"]}";
        byte[] png = LabelRenderer.render(snapshot);
        assertTrue(png[0] == (byte) 0x89 && png[1] == 0x50);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(Math.abs(image.getWidth() - 480) <= 2);
        assertTrue(Math.abs(image.getHeight() - 320) <= 2);
        assertTrue(hasBlackPixel(image));
        assertEquals("JG|mother|MD-1|1|LI1", LabelRenderer.text(snapshot, "qr"));
    }

    @Test
    void rendersFrozenFieldsAndLayoutForPrintStation() throws Exception {
        String common = "{\"kind\":\"LABEL\",\"businessCode\":\"MD-1\",\"primaryCode\":\"MD-1\","
                + "\"templateCode\":\"MOTHER-CUSTOM\",\"copies\":1,\"widthMm\":40,\"heightMm\":30,\"dpi\":300,"
                + "\"typeLabel\":\"母单\",\"brandTitle\":\"珠宝\",\"qr\":\"JG|mother|MD-1|0|LI1\","
                + "\"footerLeft\":\"\",\"footerRight\":\"\",\"layout\":{\"mode\":\"COMPACT\","
                + "\"qrWidthMm\":15,\"codeFontPt\":10,\"fieldFontPt\":8,\"emphasisFontPt\":9,"
                + "\"titleFontPt\":7,\"footerFontPt\":6},\"fields\":";
        byte[] withFields = LabelRenderer.render(common + "[{\"name\":\"客户\",\"value\":\"甲\\\"乙\"}]}");
        byte[] withoutFields = LabelRenderer.render(common + "[]}");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(withFields));
        assertTrue(Math.abs(image.getWidth() - 472) <= 2);
        assertTrue(Math.abs(image.getHeight() - 354) <= 2);
        assertTrue(hasBlackPixel(image));
        assertTrue(!Arrays.equals(withFields, withoutFields));
        assertEquals("甲\"乙", SnapshotJson.parse("{\"value\":\"甲\\\"乙\"}").get("value"));
        assertEquals("{\"copies\":1}", LabelRenderer.text(
                "{\"data\":{\"contentSnapshot\":\"{\\\"copies\\\":1}\"}}", "contentSnapshot"));
    }

    @Test
    void compactFieldsContinueUnderTheQr() throws Exception {
        String head = "{\"kind\":\"LABEL\",\"businessCode\":\"MD-1\",\"primaryCode\":\"MD-20261014-0001\","
                + "\"templateCode\":\"MOTHER-DEFAULT\",\"copies\":1,\"widthMm\":40,\"heightMm\":30,\"dpi\":300,"
                + "\"typeLabel\":\"母单\",\"brandTitle\":\"珠宝\",\"qr\":\"JG|mother|MD-1|0|LI1\","
                + "\"footerLeft\":\"\",\"footerRight\":\"\",\"layout\":{\"mode\":\"COMPACT\",\"qrWidthMm\":17},"
                + "\"fields\":";
        String longText = "内壁刻字并保留原石花纹".repeat(4);
        byte[] wrapped = LabelRenderer.render(head + "[{\"name\":\"客户\",\"value\":\"" + longText + "\"},"
                + "{\"name\":\"要求\",\"value\":\"" + longText + "\"},"
                + "{\"name\":\"期限\",\"value\":\"" + longText + "\"},"
                + "{\"name\":\"备注\",\"value\":\"" + longText + "\"}]}");
        byte[] shortText = LabelRenderer.render(head + "[{\"name\":\"客户\",\"value\":\"甲\"}]}");
        BufferedImage longImage = ImageIO.read(new ByteArrayInputStream(wrapped));
        BufferedImage shortImage = ImageIO.read(new ByteArrayInputStream(shortText));
        int left = longImage.getWidth() / 3;
        int top = (int) (longImage.getHeight() * 0.78);
        int inset = 8;
        assertTrue(hasBlackPixelIn(longImage, inset, top, left, longImage.getHeight() - inset));
        assertTrue(!hasBlackPixelIn(shortImage, inset, top, left, shortImage.getHeight() - inset));
    }

    private boolean hasBlackPixelIn(BufferedImage image, int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y += 2) {
            for (int x = left; x < right; x += 2) {
                if ((image.getRGB(x, y) & 0xffffff) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBlackPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                if ((image.getRGB(x, y) & 0xffffff) == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
