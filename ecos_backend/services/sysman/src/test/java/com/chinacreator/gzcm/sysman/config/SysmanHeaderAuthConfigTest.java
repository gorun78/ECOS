package com.chinacreator.gzcm.sysman.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * B32 sysman 信任链装配契约门（P1-15 修复的防回潮断言）。
 *
 * <p>实证背景：sysman 宿主此前从未注册 {@code HeaderAuthInterceptor}
 * （application.yml 误置死键 {@code ecos.header-auth}——common-api
 * {@code HeaderAuthAutoConfiguration} 只认 {@code ecos.security.header-auth}），
 * {@code ServiceContext} 恒空 ⇒ {@code /api/v1/security/decision} 对无 subject 请求
 * 恒 {@code 403 无用户上下文（ECOS-AUTH-010）}，场景运行历史列裁剪被 DENY 置空。</p>
 *
 * <p>判据：application.yml 必须在该 auto-config 认的键下显式 enabled=true，
 * 且不得残留死键（残留即可能被误读为"已关"）。</p>
 */
class SysmanHeaderAuthConfigTest {

    private Map<String, Object> loadApplicationYml() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(in, "application.yml 不在 sysman 测试 classpath 上");
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("application.yml 解析失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> parent, String key) {
        Object v = parent == null ? null : parent.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @Test
    @DisplayName("ecos.security.header-auth.enabled=true（auto-config 认的键）")
    void headerAuthEnabledUnderCanonicalKey() {
        Map<String, Object> yml = loadApplicationYml();
        Map<String, Object> ecos = section(yml, "ecos");
        Map<String, Object> security = section(ecos, "security");
        Map<String, Object> headerAuth = section(security, "header-auth");
        assertNotNull(headerAuth, "缺 ecos.security.header-auth 段（HeaderAuthAutoConfiguration 唯一认的键）");
        assertEquals(Boolean.TRUE, headerAuth.get("enabled"),
                "sysman 宿主必须启用 HeaderAuthInterceptor，否则 ServiceContext 恒空、decision 恒 010");
    }

    @Test
    @DisplayName("死键 ecos.header-auth 不得残留（auto-config 读不到，会被误读为已关）")
    void deadKeyAbsent() {
        Map<String, Object> yml = loadApplicationYml();
        Map<String, Object> ecos = section(yml, "ecos");
        assertFalse(ecos != null && ecos.containsKey("header-auth"),
                "ecos.header-auth 是死键（HeaderAuthAutoConfiguration 不读），残留即假配置");
    }

    @Test
    @DisplayName("HeaderAuthAutoConfiguration 的装配键与本 yml 键同形（防键名再次漂移）")
    void canonicalKeyShape() {
        Map<String, Object> yml = loadApplicationYml();
        Map<String, Object> ecos = section(yml, "ecos");
        Map<String, Object> security = section(ecos, "security");
        assertTrue(security != null && security.containsKey("header-auth"),
                "键必须是 ecos.security.header-auth，与 aiming/workspace 服务 yml 同口径");
    }
}
