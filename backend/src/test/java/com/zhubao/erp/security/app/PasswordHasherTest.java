package com.zhubao.erp.security.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesAndVerifiesPasswordWithoutStoringPlainText() {
        String encoded = passwordHasher.hash("secret-password");

        assertThat(encoded).startsWith("pbkdf2_sha256$120000$");
        assertThat(encoded).doesNotContain("secret-password");
        assertThat(passwordHasher.matches("secret-password", encoded)).isTrue();
        assertThat(passwordHasher.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void rejectsMalformedHash() {
        assertThat(passwordHasher.matches("secret-password", "plain-text")).isFalse();
        assertThat(passwordHasher.matches("secret-password", "")).isFalse();
    }
}
