package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 详细设计-01 W35（M0）— OPA fail-close 常量化：OPA 不可用时一律拒绝，无 fail-open 分支。
 *
 * <p>用不可达 URL {@code http://nonexistent.invalid:1} 触发连接异常 → 期望 {@code allow=false}
 * + {@code fallback=DENY_OPA_DOWN} + {@code eccode=ECOS-SEC-502}。若代码回退到 fail-open
 * 分支（基于 RBAC 简单放行），此处断言会失败。</p>
 */
@DisplayName("OPA fail-close 常量化（W35）")
class OpaFailCloseTest {

    @Test
    @DisplayName("OPA 不可用 → allow=false + DENY_OPA_DOWN（写操作无裁决即拒）")
    void opaDownAlwaysDeny() {
        String dir = System.getProperty("java.io.tmpdir");
        // 未设置的目录走 OpaPolicyDirResolver 的 classpath 兜底；此处只是初始化 init 副作用
        OpaPolicyService svc = new OpaPolicyService("http://nonexistent.invalid:1", dir);
        Map<String, Object> r = svc.evaluate("proj-allow", Map.of(
                "role", "ROLE_SUPER_ADMIN", "action", "write"));
        assertEquals(Boolean.FALSE, r.get("allow"), "OPA 宕机必须 fail-close DENY");
        assertEquals("DENY_OPA_DOWN", r.get("fallback"));
        assertEquals("ECOS-SEC-502", r.get("eccode"), "W35：fail-close 需标注 ECOS-SEC-502");
        // C.2.5 ③：obligations 结构化落地（fail-close 场景明确 deniedReason）
        Object obligations = r.get("obligations");
        assertNotNull(obligations, "obligations 必须存在（可为空数组）");
        assertInstanceOf(List.class, obligations);
    }

    @Test
    @DisplayName("policy 名称注入防护仍生效（SAFE_NAME 白名单）")
    void policyNameInjectionGuarded() {
        OpaPolicyService svc = new OpaPolicyService("http://nonexistent.invalid:1",
                System.getProperty("java.io.tmpdir"));
        assertThrows(IllegalArgumentException.class,
                () -> svc.evaluate("../../etc/passwd", Map.of()));
    }
}
