package com.chinacreator.gzcm.sysman.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D101 门禁：重置密码端点自产的临时密码必须满足 {@code UserServiceImpl} 的复杂度策略。
 *
 * <p><b>缺陷形态（2026-11-14 活体实证）</b>：旧 {@code PWD_CHARSET} 含 {@code #}，
 * 而策略正则只收 {@code [A-Za-z\d@$!%*?&]}；且旧实现只做 12 次独立随机、不保证
 * 四类字符齐全。两者叠加使该端点约四成概率「自己生成、自己拒绝」，表现为
 * {@code POST /system/users/{id}/reset-password} 返回 <b>500</b> 且不带原因。</p>
 *
 * <p>本门禁用大批量抽样把「概率性自拒」变成确定性判定：只要字符集再混入策略外字符、
 * 或删掉「四类各落一位」的保底，下面两条断言必然转红。</p>
 */
@DisplayName("D101：临时密码生成器必须自洽于密码复杂度策略")
class UserControllerTempPasswordTest {

    /** 与 UserServiceImpl.PASSWORD_COMPLEXITY_REGEX 逐字同源（改一处必须同批改另一处）。 */
    private static final String POLICY_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$";

    @Test
    @DisplayName("批量抽样 2000 次，全部必须通过复杂度正则（消除概率性 500）")
    void generatedPasswordsAlwaysSatisfyPolicy() {
        for (int i = 0; i < 2000; i++) {
            String pwd = UserController.genTempPassword();
            assertTrue(pwd.matches(POLICY_REGEX),
                    "第 " + i + " 个临时密码违反策略（会让 reset-password 返回 500）: " + pwd);
        }
    }

    @Test
    @DisplayName("长度固定 12 且不含策略外字符（# 等）")
    void generatedPasswordsHaveStableShape() {
        for (int i = 0; i < 500; i++) {
            String pwd = UserController.genTempPassword();
            assertEquals12(pwd);
            assertFalse(pwd.contains("#"), "策略正则不收 '#'，出现即违规: " + pwd);
            assertFalse(pwd.contains("0") || pwd.contains("O") || pwd.contains("1")
                            || pwd.contains("I") || pwd.contains("l"),
                    "易混淆字符应被排除: " + pwd);
        }
    }

    @Test
    @DisplayName("随机性自检：连续两个不相等（防止生成器退化成常量）")
    void generatedPasswordsAreNotConstant() {
        assertNotEquals(UserController.genTempPassword(), UserController.genTempPassword());
    }

    private static void assertEquals12(String pwd) {
        assertTrue(pwd.length() == 12, "长度应为 12，实得 " + pwd.length() + ": " + pwd);
    }
}
