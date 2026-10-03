package com.chinacreator.gzcm.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.AnnotationUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W11（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F F00-04 / C.1.1-C.1.2，M1）
 * — 过滤器链序统一为 {@code HIGHEST_PRECEDENCE+n} 家族，且顺序满足 C.1.2 约束：
 * {@code VersionPrefixRewrite < RequestContext < EcosTrustHeader < SecurityHeaders < RateLimit < Quota}。
 *
 * <p>6 用例（验收命令 {@code -Dtest='FilterOrderTest'}）：
 * <ol>
 *   <li>VersionPrefixRewriteFilter.order == HIGHEST_PRECEDENCE+10</li>
 *   <li>RequestContextFilter.order == HIGHEST_PRECEDENCE+11</li>
 *   <li>EcosTrustHeaderFilter.order == HIGHEST_PRECEDENCE+20</li>
 *   <li>SecurityHeadersFilter.order == HIGHEST_PRECEDENCE+30</li>
 *   <li>链序满足 C.1.2：VPR &lt; RCT &lt; ETH &lt; SHF &lt; RateLimit(5) &lt; Quota(6)，
 *       且 SecurityConfig 含 addFilterBefore(jwtAuthenticationFilter 字样</li>
 *   <li>QuotaFilter 必带 @Order（W11 前无注解是缺陷）</li>
 * </ol>
 * 反射读 @Order 注解值，不启动 Spring。
 */
class FilterOrderTest {

    private static Path moduleRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path guess = cwd;
        while (guess != null) {
            // backend 根：同时含 gateway/ 与 services/sysman/ 目录
            if (Files.isDirectory(guess.resolve("gateway/src/main/java"))
                    && Files.isDirectory(guess.resolve("services/sysman"))) {
                return guess;
            }
            guess = guess.getParent();
        }
        return cwd;
    }

    private static String readRelative(Path root, String relative) {
        Path p = root.resolve(relative);
        try {
            return Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("源文本读取失败: " + p, e);
        }
    }

    private static int orderOf(Class<?> type) {
        Order o = AnnotationUtils.findAnnotation(type, Order.class);
        assertNotNull(o, type.getSimpleName() + " 必须带 @Order（W11 链序统一）");
        return o.value();
    }

    /** tc1 */
    @Test
    @DisplayName("tc1 VersionPrefixRewriteFilter.order == HIGHEST_PRECEDENCE+10")
    void versionPrefixRewriteOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 10, orderOf(VersionPrefixRewriteFilter.class));
    }

    /** tc2 */
    @Test
    @DisplayName("tc2 RequestContextFilter.order == HIGHEST_PRECEDENCE+11")
    void requestContextOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 11, orderOf(RequestContextFilter.class));
    }

    /** tc3 */
    @Test
    @DisplayName("tc3 EcosTrustHeaderFilter.order == HIGHEST_PRECEDENCE+20")
    void ecosTrustHeaderOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 20, orderOf(EcosTrustHeaderFilter.class));
    }

    /** tc4 */
    @Test
    @DisplayName("tc4 SecurityHeadersFilter.order == HIGHEST_PRECEDENCE+30")
    void securityHeadersOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 30, orderOf(SecurityHeadersFilter.class));
    }

    /** tc5：C.1.2 数值序 + JwtAuthenticationFilter 走 addFilterBefore(UsernamePassword) */
    @Test
    @DisplayName("tc5 C.1.2 链序 VPR<RCT<ETH<SHF<RateLimit<Quota + SecurityConfig addFilterBefore")
    void chainOrderSatisfiesC12() {
        int vpr = orderOf(VersionPrefixRewriteFilter.class);
        int rct = orderOf(RequestContextFilter.class);
        int eth = orderOf(EcosTrustHeaderFilter.class);
        int shf = orderOf(SecurityHeadersFilter.class);
        int rate = orderOf(RateLimitFilter.class);
        int quota = orderOf(QuotaFilter.class);

        Integer[] seq = {vpr, rct, eth, shf, rate, quota};
        for (int i = 0; i < seq.length - 1; i++) {
            assertTrue(seq[i] < seq[i + 1],
                    "C.1.2 链序必须严格递增，段 [" + i + "]=" + seq[i] + " 应 < [" + (i + 1) + "]=" + seq[i + 1]);
        }
        // Rate 显式 @Order(5)、Quota @Order(6)（W11 统一为数值家族尾部）
        assertEquals(5, rate);
        assertEquals(6, quota);

        // JwtAuthenticationFilter 位于 Spring Security 链内（addFilterBefore UsernamePassword）
        Path root = moduleRoot();
        String secCfg = readRelative(root,
                "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java");
        assertTrue(secCfg.contains("addFilterBefore(jwtAuthenticationFilter")
                        || secCfg.contains("addFilterBefore(jwtAuthenticationFilter,"),
                "SecurityConfig 必须 addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter)");
    }

    /** tc6：QuotaFilter 必带 @Order（W11 收口前的历史缺陷：无注解） */
    @Test
    @DisplayName("tc6 QuotaFilter 必带 @Order（原无注解是缺陷，现应为 @Order(6)）")
    void quotaFilterMustHaveOrder() {
        Order o = AnnotationUtils.findAnnotation(QuotaFilter.class, Order.class);
        assertNotNull(o, "QuotaFilter 无 @Order 是 W11 缺陷，必须显式声明链序");
        assertEquals(6, o.value());
    }

    /** 占位：保留 Comparator 引用防未用 import */
    @SuppressWarnings("unused")
    private static Comparator<Class<?>> byOrder() {
        return Comparator.comparingInt(FilterOrderTest::orderSafe);
    }

    private static int orderSafe(Class<?> type) {
        Order o = AnnotationUtils.findAnnotation(type, Order.class);
        return o == null ? Integer.MAX_VALUE : o.value();
    }
}
