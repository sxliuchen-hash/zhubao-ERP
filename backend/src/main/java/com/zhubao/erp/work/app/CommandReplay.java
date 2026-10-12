package com.zhubao.erp.work.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.CommandRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 请求编号、账号和内容摘要的共同校验。 */
final class CommandReplay {

    private CommandReplay() {
    }

    static CommandCall normalize(CommandCall call) {
        if (call == null) {
            throw new BusinessException("请提供命令请求");
        }
        String requestId = text(call.requestId());
        if (!requestId.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new BusinessException("请求编号无效");
        }
        String actorId = required(call.actorId(), "操作人不能为空", 64);
        String action = required(call.action(), "命令动作不能为空", 64);
        String targetType = required(call.targetType(), "单据类型不能为空", 32);
        String targetCode = required(call.targetCode(), "单据编号不能为空", 90);
        String payload = text(call.canonicalPayload()).replaceAll("\\s+", " ");
        checkLength(payload, 2000, "命令内容");
        return new CommandCall(requestId, actorId, action, targetType, targetCode, payload);
    }

    static String hash(CommandCall call) {
        String payload = call.action() + "\n" + call.targetType() + "\n" + call.targetCode() + "\n" + call.canonicalPayload();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    static void assertReplay(CommandCall call, String hash, CommandRequest existing) {
        if (!call.actorId().equals(existing.actorId())) {
            throw new PermissionDeniedException("该请求不能跨账号重放");
        }
        if (!call.action().equals(existing.action())
                || !call.targetType().equals(existing.targetType())
                || !call.targetCode().equals(existing.targetCode())
                || !hash.equals(existing.payloadHash())) {
            throw new BusinessException("同一请求的内容不一致");
        }
    }

    static String requireResult(String result) {
        String text = result == null ? "" : result.trim();
        if (text.isEmpty()) {
            throw new BusinessException("成功结果不能为空");
        }
        checkLength(text, 2000, "成功结果");
        return text;
    }

    private static String required(String value, String message, int max) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        checkLength(text, max, "字段");
        return text;
    }

    private static void checkLength(String value, int max, String label) {
        if (value.length() > max) {
            throw new BusinessException(label + "超过可保存长度");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
