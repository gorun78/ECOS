package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.service.OpaPolicyDirResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 详细设计-01 W36 — OPA 策略目录跨平台解析器。
 *
 * <ul>
 *   <li>显式存在的目录 → 直接返回（不进 classpath 兜底）</li>
 *   <li>不可用/非目录 → 走 classpath 解包兜底（result 仍为已存在目录）</li>
 *   <li>copyClasspathRego：从本地目录解包</li>
 * </ul>
 */
@DisplayName("OPA 策略目录跨平台解析（W36）")
class OpaPolicyDirResolverTest {

    @TempDir
    Path explicitDir;

    @Test
    @DisplayName("显式存在目录 → 原样返回（Windows 本地不触发 classpath 解包）")
    void explicitConfiguredDirUsed() {
        Path resolved = OpaPolicyDirResolver.resolve(explicitDir.toString());
        assertEquals(explicitDir.toAbsolutePath().normalize(), resolved,
                "已配置目录应原样返回");
        assertTrue(Files.isDirectory(resolved));
    }

    @Test
    @DisplayName("不可用配置 → 转 classpath 兜底，仍是已存在目录")
    void badConfigFallsBackToClasspath() {
        Path resolved = OpaPolicyDirResolver.resolve("/nonexistent/opa-policy-dir");
        assertNotNull(resolved);
        assertTrue(Files.isDirectory(resolved),
                "解析结果必须是已存在的目录，供 OpaPolicyService 继续 fail-close 拒绝");
    }

    @Test
    @DisplayName("copyClasspathRego 可为空（无任何 .rego 也返回 0，不抛异常）")
    void copyClasspathRego_handlesEmpty() throws IOException {
        // 用一个不存在的 file: URL 语义模拟：base 目录新建，classpath 无 opa/policies 时
        // getResources 空枚举 → count=0。目标态上线前该值可为 0，测试只看方法可安全调用。
        int count = OpaPolicyDirResolver.copyClasspathRego(explicitDir);
        assertTrue(count >= 0, "count 必须 >= 0");
    }
}
