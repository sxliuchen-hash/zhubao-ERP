package com.zhubao.erp.business.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 用当前单据完整快照形成管理员改单的前置版本。 */
final class OrderMaintenanceRevision {
    private OrderMaintenanceRevision() {
    }

    static String of(Object record) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(record.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算改单版本", ex);
        }
    }
}
