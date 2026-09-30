package com.chinacreator.gzcm.common.security.anonymous;

import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.Category;
import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 W05 门禁 — 匿名端点单一真源。
 *
 * <p>断言对象是 {@link AnonymousEndpointRegistry} 本身（非测试内复制字符串），
 * 新增未登记 permitAll 即由此类判 FAIL（设计目标：三处集合不再互相偏离）。</p>
 */
class AnonymousEndpointInventoryTest {

    @Test
    @DisplayName("permission 面必须覆盖认证 + 健康 + 错误兜底三大族")
    void anonymousCoversAuthHealthError() {
        Set<String> anon = new TreeSet<>(AnonymousEndpointRegistry.permitAllPatterns());
        assertTrue(anon.stream().anyMatch(p -> p.contains("/api/v1/auth/**") || p.contains("/api/auth/**")),
                "auth 双路径必须匿名可达");
        assertTrue(anon.contains("/health"), "健康探测必须匿名");
        assertTrue(anon.contains("/actuator/health"), "actuator 健康必须匿名");
        assertTrue(anon.contains("/error"), "错误兜底必须匿名");
    }

    @Test
    @DisplayName("dual-path 展开：/api/v1/X/** 命中必须同时列出 /api/X/**")
    void dualPathIsExpanded() {
        Set<String> all = new TreeSet<>(AnonymousEndpointRegistry.permitAllPatterns());
        assertTrue(all.contains("/api/v1/auth/**"), "auth v1 缺失");
        assertTrue(all.contains("/api/auth/**"), "auth 裸路径缺失");
        // 一个 non-dual 条目不应被误展开
        assertTrue(anonNotDual("/actuator/health"));
    }

    private boolean anonNotDual(String p) {
        for (Entry e : AnonymousEndpointRegistry.all()) {
            if (e.getPattern().equals(p)) return !e.isDualPath();
        }
        return false;
    }

    @Test
    @DisplayName("clearance 豁免 ⊇ 匿名（bypass 一级校验，含 auth/health 以下全部）")
    void clearanceExemptSupersetOfAnonymous() {
        Set<String> anon = new TreeSet<>(AnonymousEndpointRegistry.permitAllPatterns());
        Set<String> exempt = new TreeSet<>(AnonymousEndpointRegistry.clearanceExemptPatterns());
        assertTrue(exempt.containsAll(anon),
                "clearance-exempt 必须包含全部 anonymous 路径（豁免等于是放行之上放行）");
    }

    @Test
    @DisplayName("engine 业务路径必须 NOT 在 clearance 豁免面（防 blanket 过度豁免）")
    void engineBusinessNotBlanketExempt() {
        // H9-T2b 举证：整族放行会被 PMO-B 判非收敛
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/data/lineage"),
                "engine data 业务必须继续走准入校验，空白豁免会破坏 H9-T2b");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/data/quality"));
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/data/udf"));
    }

    @Test
    @DisplayName("health 族（含认知/kb 引擎）必须匿名放行")
    void engineHealthIsAnonymous() {
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/cognitive/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/knowledge/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/knowledge/health"));
    }

    @Test
    @DisplayName("knowledge/extract 服务间豁免必须仅双路径桶内，业务路径未穿透")
    void extractOnlyExemptListed() {
        // extract 是 CLEARANCE_EXEMPT（bypass clearance）但不属于 ANONYMOUS（SecurityConfig 不放行）
        Entry extract = AnonymousEndpointRegistry.all().stream()
                .filter(e -> e.getPattern().equals("/api/v1/knowledge/extract/**"))
                .findFirst().orElseThrow();
        assertEquals(Category.CLEARANCE_EXEMPT, extract.getCategory());
        // permitAllPatterns 只列 ANONYMOUS，extract 不应出现
        assertFalse(new TreeSet<>(AnonymousEndpointRegistry.permitAllPatterns())
                .stream().anyMatch(p -> p.startsWith("/api/v1/knowledge/extract")));
    }
}
