package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W36 — OPA 跨平台策略目录解析（{@link OpaPolicyService#resolvePolicyDir}，同包访问）。
 *
 * <p>解析优先级：显式配置 {@code opa.policy-dir} → {@code ECOS_OPA_POLICY_DIR} env
 * → 兜底 classpath 解包（{@code ${java.io.tmpdir}/ecos-opa}）。本测试锁定前两级契约：
 * 显式配置优先于一切；空白 / null 配置回落到 tmpdir 兜底路径（绝对路径）。</p>
 */
class OpaPolicyDirResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("显式配置优先 → 返回规范绝对路径")
    void explicitConfig_wins() {
        Path explicit = tempDir.toAbsolutePath().normalize();

        Path result = OpaPolicyService.resolvePolicyDir(explicit.toString());
        assertEquals(explicit, result,
                "显式配置应原样（规范化）返回，优先级最高");
        assertTrue(result.isAbsolute(), "显式配置解析结果必须是绝对路径");
        assertNotNull(result, "显式配置解析结果不可为空");
    }

    @Test
    @DisplayName("空配置(空串) → 回落 tmpdir 兜底目录")
    void blankConfig_fallsToTmpDefault() {
        Path result = OpaPolicyService.resolvePolicyDir("");
        assertNotNull(result, "空白配置不应返回 null");
        assertTrue(result.isAbsolute(), "兜底目录必须是绝对路径");
        // 无 env / 无 classpath 时回落 = ${java.io.tmpdir}/ecos-opa
        String tmp = System.getProperty("java.io.tmpdir").replace('\\', '/');
        String resolved = result.toString().replace('\\', '/');
        assertTrue(resolved.startsWith(tmp),
                "空白配置的兜底目录应位于 java.io.tmpdir 之下: " + resolved);
    }

    @Test
    @DisplayName("null 配置 → 回落到有效绝对路径（env 或 classpath 解包）")
    void nullConfig_fallsToTmpDefault() {
        Path result = OpaPolicyService.resolvePolicyDir(null);
        assertNotNull(result, "null 配置仍必须解析出有效目录");
        assertTrue(result.isAbsolute(), "null 配置解析结果必须是绝对路径");
        // classpath 解包兜底落在 tmpdir/ecos-opa；无论走 env 还是解包分支都应是绝对路径且非空
        assertFalse_blank(result.toString());
    }

    private static void assertFalse_blank(String s) {
        if (s == null || s.isBlank()) {
            throw new AssertionError("策略目录字符串不可为空");
        }
    }
}
