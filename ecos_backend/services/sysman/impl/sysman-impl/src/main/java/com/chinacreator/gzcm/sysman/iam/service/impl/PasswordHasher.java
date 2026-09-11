package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 基于PBKDF2的密码哈希工具。
 * 兼容旧的SHA-256存储：新密码使用PBKDF2，验证时自动识别前缀。
 */
class PasswordHasher {

    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;
    private static final String PREFIX = "pbkdf2$";

    String hash(String rawPassword) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(salt);
            byte[] hash = pbkdf2(rawPassword, salt, ITERATIONS, KEY_LENGTH);
            return PREFIX + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    boolean matches(String rawPassword, String stored) {
        if (stored == null) {
            return false;
        }
        if (stored.startsWith(PREFIX)) {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) {
                return false;
            }
            try {
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                byte[] actual = pbkdf2(rawPassword, salt, iterations, expected.length * 8);
                return slowEquals(expected, actual);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                return false;
            }
        }
        // 兼容旧SHA-256（Base64）
        try {
            byte[] legacy = sha256(rawPassword);
            return Base64.getEncoder().encodeToString(legacy).equals(stored);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private byte[] pbkdf2(String rawPassword, byte[] salt, int iterations, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, keyLength);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGO);
        return skf.generateSecret(spec).getEncoded();
    }

    private byte[] sha256(String rawPassword) throws NoSuchAlgorithmException {
        return java.security.MessageDigest.getInstance("SHA-256").digest(rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private boolean slowEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}


