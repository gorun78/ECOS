package com.chinacreator.gzcm.sysman.boot.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 缺陷 D86 门禁（登录侧）：口令比对必须同时识别 BCrypt 与 PBKDF2 两种存量形态。
 *
 * <p>登录表历史行是 BCrypt，而 IAM 主数据侧（{@code td_user.password}）产出的是
 * {@code pbkdf2$iter$salt$hash}。若登录端只认 BCrypt，任何 PBKDF2 口令都会走
 * "口令错误"分支，表现为正确口令也 401——正是 D86 的可见症状。</p>
 */
class AuthControllerPasswordFormTest {

    /** 口令比对是纯函数，协作者传 null 即可（本用例不触达登录链路）。 */
    private final AuthController controller = new AuthController(null, null, null);

    private static String pbkdf2(String raw) {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(raw.toCharArray(), salt, 65536, 256);
        byte[] hash;
        try {
            hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return "pbkdf2$65536$" + java.util.Base64.getEncoder().encodeToString(salt) + "$"
                + java.util.Base64.getEncoder().encodeToString(hash);
    }

    @Test
    @DisplayName("PBKDF2 存量口令：正确口令通过（此前一律 401）")
    void pbkdf2CorrectPasswordPasses() {
        assertTrue(controller.matchesPasswordForTest("Verify@2026Aa", pbkdf2("Verify@2026Aa")));
    }

    @Test
    @DisplayName("PBKDF2 存量口令：错误口令仍拒绝")
    void pbkdf2WrongPasswordRejected() {
        assertFalse(controller.matchesPasswordForTest("Wrong@2026Aa", pbkdf2("Verify@2026Aa")));
    }

    @Test
    @DisplayName("BCrypt 存量口令：两种结果都正确")
    void bcryptStillWorks() {
        String hash = new BCryptPasswordEncoder().encode("Verify@2026Aa");
        assertTrue(controller.matchesPasswordForTest("Verify@2026Aa", hash));
        assertFalse(controller.matchesPasswordForTest("Wrong@2026Aa", hash));
    }

    @Test
    @DisplayName("空/畸形存储值不得放行（fail-closed）")
    void malformedStoredValueNeverPasses() {
        assertFalse(controller.matchesPasswordForTest("Verify@2026Aa", null));
        assertFalse(controller.matchesPasswordForTest("Verify@2026Aa", ""));
        assertFalse(controller.matchesPasswordForTest("Verify@2026Aa", "pbkdf2$broken"));
        assertFalse(controller.matchesPasswordForTest("Verify@2026Aa", "NOLOGIN:seed-entmgmt-v2"));
    }
}
