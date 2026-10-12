package com.zhubao.erp.document.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 证据引用的规范化业务键。可空项变成空串，避免依赖数据库对 NULL 的唯一性。
 * 摘要只用于查找；保存前仍按原始维度核对，不能用超长拼接代替字段。
 */
public final class EvidenceLinkKey {

    public static final int TARGET_TYPE_MAX = 32;
    public static final int TARGET_CODE_MAX = 90;
    public static final int STAGE_MAX = 16;
    public static final int SCENE_MAX = 16;
    public static final int OPERATION_TYPE_MAX = 32;
    public static final int OPERATION_CODE_MAX = 96;
    public static final int PROCESS_MAX = 20;
    public static final int CLAUSE_MAX = 32;
    public static final int ATTACHMENT_MAX = 80;

    private EvidenceLinkKey() {
    }

    public static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public static String hash(
            String targetType,
            String targetCode,
            String stage,
            String scene,
            String operationType,
            String operationCode,
            String processName,
            String clauseNo,
            String attachmentCode
    ) {
        String raw = String.join(
                "\u001f",
                text(targetType),
                text(targetCode),
                text(stage),
                text(scene),
                text(operationType),
                text(operationCode),
                text(processName),
                text(clauseNo),
                text(attachmentCode)
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算证据引用摘要", ex);
        }
    }

    public static boolean sameDimensions(BusinessEvidenceLink left, BusinessEvidenceLink right) {
        return text(left.targetType()).equals(text(right.targetType()))
                && text(left.targetCode()).equals(text(right.targetCode()))
                && text(left.stage()).equals(text(right.stage()))
                && text(left.scene()).equals(text(right.scene()))
                && text(left.operationType()).equals(text(right.operationType()))
                && text(left.operationCode()).equals(text(right.operationCode()))
                && text(left.processName()).equals(text(right.processName()))
                && text(left.clauseNo()).equals(text(right.clauseNo()))
                && text(left.attachmentCode()).equals(text(right.attachmentCode()));
    }
}
