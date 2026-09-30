package com.chinacreator.gzcm.sysman.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PMO-74 H9-T2b — 第二张豁免表（注册表 {@code excludePathPatterns}）收口的静态判定。
 *
 * <p>断言对象是 {@link ClearanceMvcConfig} 的<b>真实常量</b>而非测试内复制的字符串，
 * 因此配置改动会立刻反映到本用例。判定语义：路径先被 INCLUDE 命中，再未被 EXCLUDE 命中，
 * 才算「进得了准入等级校验」。</p>
 *
 * <p>样本路径全部来自实测的 {@code @RequestMapping} 字面量（见 §9.71 分母表）。</p>
 */
class ClearanceMvcConfigT2bTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static boolean intercepted(String path) {
        boolean included = matchAny(ClearanceMvcConfig.INCLUDE_PATTERNS, path);
        boolean excluded = matchAny(ClearanceMvcConfig.EXCLUDE_PATTERNS, path);
        return included && !excluded;
    }

    private static boolean matchAny(String[] patterns, String path) {
        for (String p : patterns) {
            if (MATCHER.match(p, path)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("engine 业务端点（v1 与裸路径两种形态）现在必须进得了准入校验")
    void engineBusinessEndpointsAreNowGuarded() {
        assertTrue(intercepted("/api/v1/engine/data/lineage"),
                "DataLineageController 的业务路径应受等级校验");
        assertTrue(intercepted("/api/v1/engine/data/quality"),
                "QualityController 的业务路径应受等级校验");
        assertTrue(intercepted("/api/v1/engine/data/udf"),
                "UdfController 的业务路径应受等级校验");
    }

    @Test
    @DisplayName("裸 /api/** 双路径形态纳入注册面（旧 INCLUDE 只有 /api/v1/** 时的漏检已闭）")
    void bareApiPrefixIsNowGuarded() {
        assertTrue(intercepted("/api/integration/metadata"),
                "IntegrationMetadataController 的裸路径应受等级校验");
        assertTrue(intercepted("/api/knowledge-bases"),
                "裸 /api/knowledge-bases 应受等级校验");
    }

    @Test
    @DisplayName("差分对照：旧注册面 {/api/v1/**} 确实漏掉裸路径 ⇒ 新增条目是承重的")
    void oldIncludeSetWouldHaveMissedBarePaths() {
        List<String> legacyInclude = List.of("/api/v1/**");
        assertFalse(legacyInclude.stream().anyMatch(p -> MATCHER.match(p, "/api/integration/metadata")),
                "对照失败：旧注册面本应匹配不到裸路径");
        assertTrue(MATCHER.match("/api/v1/engine/**", "/api/v1/engine/data/lineage"),
                "对照失败：旧豁免本应整族放行 engine ⇒ 收窄动作是承重的");
    }

    @Test
    @DisplayName("豁免面只留匿名可达类：auth / health 族仍放行，且与 SecurityConfig permitAll 同集合")
    void anonymousReachableSurfaceStaysExcluded() {
        assertFalse(intercepted("/api/v1/auth/login"));
        assertFalse(intercepted("/api/auth/login"));
        assertFalse(intercepted("/api/v1/engine/cognitive/health"),
                "cognitive 开放健康检查（SecurityConfig permitAll）不得被新拦截");
        assertFalse(intercepted("/api/v1/engine/knowledge/health"),
                "kb 引擎健康检查（SecurityConfig permitAll）不得被新拦截");
        assertFalse(intercepted("/api/v1/knowledge/health"));
        assertFalse(intercepted("/api/health"));
        assertFalse(intercepted("/health"));
    }

    /** known-0 对照：不在注册面上的路径根本不经过本拦截器（既不被放行也不被校验）。 */
    @Test
    @DisplayName("对照-known0 /actuator/health 不在注册面内")
    void outsideRegistrationSurfaceIsUntouched() {
        assertFalse(intercepted("/actuator/health"));
        assertFalse(intercepted("/error"));
    }

    @Test
    @DisplayName("knowledge/extract 暂留豁免（依赖 H10 服务间凭证，见 T2b-2）")
    void extractStillExcludedPendingH10() {
        assertFalse(intercepted("/api/v1/knowledge/extract/run"),
                "无凭证服务间调用（cognitive→kb）尚未收口，本 Task 不得改变其放行语义");
    }
}
