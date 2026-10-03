package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.2.5 / W35 — OPA fail-close 常量化（DENY all times）。
 *
 * <p>把 opaUrl 指向不可连的端口（localhost:1，连接被直接拒绝）以模拟 OPA 宕机：
 * evaluate 应返回 allow=false + source="fallback" + fallback="DENY_OPA_DOWN"，
 * 而非 fail-open。同时验证 {@link OpaPolicyService#FAIL_CLOSE} 常量恒为 true
 * 且策略名注入被 SAFE_NAME 白名单拒绝。</p>
 */
@ExtendWith(MockitoExtension.class)
class OpaFailCloseTest {

    @Mock
    AuditEventProducer auditProducer;

    @TempDir
    Path tempDir;

    OpaPolicyService service;

    @BeforeEach
    void setup() throws IOException {
        // 端口 1 无监听 → 连接立即拒绝 → 触发 catch 分支 fail-close
        service = new OpaPolicyService("http://localhost:1", tempDir.toString(), auditProducer);
    }

    @Test
    @DisplayName("OPA 宕机 → evaluate 返回 fail-close DENY 兜底")
    void opaDown_evaluate_returnsDenyFallback() {
        Map<String, Object> result = service.evaluate("test_policy", Map.of("input", "x"));

        assertEquals(Boolean.FALSE, result.get("allow"), "OPA 不可用必须 fail-close → allow=false");
        assertEquals("fallback", result.get("source"), "来源必须标记为 fallback");
        assertEquals("DENY_OPA_DOWN", result.get("fallback"), "兜底原因必须 = DENY_OPA_DOWN");
        assertEquals("test_policy", result.get("policy"), "策略名应回填");
        // obligations 必须为空（无裁决 = 无授权动作）
        assertTrue(((java.util.List<?>) result.get("obligations")).isEmpty(),
                "fail-close 时 obligations 必须为空");
    }

    @Test
    @DisplayName("FAIL_CLOSE 常量恒为 true")
    void opaDown_failCloseConstantIsTrue() {
        assertTrue(OpaPolicyService.FAIL_CLOSE, "fail-close 必须硬编码为 true，禁 fail-open 降级");
    }

    @Test
    @DisplayName("策略名注入（路径遍历）→ IllegalArgumentException")
    void opaPolicyNameInjection_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate("../../etc/passwd", Map.of("input", "x")),
                "含路径遍历的策略名必须被 SAFE_NAME 白名单拒绝");
    }

    @Test
    @DisplayName("getPolicyDir 反映构造时给定的策略目录")
    void getPolicyDir_reflectsConfiguredPath() {
        assertEquals(tempDir.toAbsolutePath().normalize(), service.getPolicyDir(),
                "getPolicyDir 应回显 resolvePolicyDir(显式配置) 的规范绝对路径");
        assertTrue(service.getPolicyDir().isAbsolute(), "策略目录必须是绝对路径");
    }
}
