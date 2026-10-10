package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Predicate;

/**
 * 给人眼核对的短验证码。随单号稳定生成，已占用时加长，避免两张单相同。
 */
public final class VerificationCodes {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private VerificationCodes() {
    }

    public static String derive(String orderCode, Predicate<String> taken) {
        byte[] digest = digest(orderCode);
        for (int length = 4; length <= digest.length; length++) {
            String candidate = encode(digest, length);
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("验证码生成失败，请重试");
    }

    private static byte[] digest(String orderCode) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(orderCode.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String encode(byte[] digest, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int alphabetIndex = (digest[index] & 0xff) % ALPHABET.length;
            builder.append(ALPHABET[alphabetIndex]);
        }
        return builder.toString();
    }
}
