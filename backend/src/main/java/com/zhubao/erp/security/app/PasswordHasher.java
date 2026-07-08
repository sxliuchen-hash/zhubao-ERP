package com.zhubao.erp.security.app;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private final SecureRandom random = new SecureRandom();

    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt, DEFAULT_ITERATIONS);
        return HASH_PREFIX + "$" + DEFAULT_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        String[] parts = encodedHash.split("\\$");
        if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] derive(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, HASH_BYTES * 8);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException ex) {
            throw new IllegalStateException("Invalid password hash spec", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hash algorithm is unavailable", ex);
        }
    }
}
