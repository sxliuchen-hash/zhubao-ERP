package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
            "123456",
            "12345678",
            "admin123",
            "password",
            "password123",
            "qwerty123",
            "welcome123"
    );

    public void assertAcceptable(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        if (!rawPassword.equals(rawPassword.trim())) {
            throw new BusinessException("密码不能包含首尾空白");
        }
        String normalizedPassword = rawPassword.toLowerCase(Locale.ROOT);
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (COMMON_WEAK_PASSWORDS.contains(normalizedPassword)) {
            throw new BusinessException("密码属于常见弱口令");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw new BusinessException("密码长度至少 8 位");
        }
        if (!normalizedUsername.isBlank() && normalizedPassword.equals(normalizedUsername)) {
            throw new BusinessException("密码不能与账号名相同");
        }
        if (!containsLetter(rawPassword) || !containsDigit(rawPassword)) {
            throw new BusinessException("密码必须同时包含字母和数字");
        }
    }

    private boolean containsLetter(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    private boolean containsDigit(String value) {
        return value.chars().anyMatch(Character::isDigit);
    }
}
