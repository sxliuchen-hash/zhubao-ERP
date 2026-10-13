package com.zhubao.print;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelRendererTest {

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