package com.chinacreator.gzcm.sysman.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置 — 注册 ClearanceInterceptor。
 *
 * <p>详细设计-00 W05 (C.3.2)：exclude 名单由 {@link com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry}
 * 单源驱动（ANONYMOUS + CLEARANCE_EXEMPT 两类展开）。与 SecurityConfig permitAll 同源，
 * 避免三处集合互相偏离（现状 SecurityConfig 8 条 / ClearanceMvcConfig 9 条 / Interceptor 内
 * 前缀 9 条）。测试门禁 = AnonymousEndpointInventoryTest；H9-T2b 静态判定见
 * ClearanceMvcConfigT2bTest（仍读 {@link #EXCLUDE_PATTERNS}，本字段由 registry 派生）。</p>
 */
@Configuration
public class ClearanceMvcConfig implements WebMvcConfigurer {

    private final ClearanceInterceptor clearanceInterceptor;

    /**
     * H9-T2b：注册面必须覆盖双路径形态。
     */
    static final String[] INCLUDE_PATTERNS = {"/api/v1/**", "/api/**"};

    /**
     * 豁免面候选：由 AnonymousEndpointRegistry 单源派生（保持既有 EXCLUDE_PATTERNS 常量可读）。
     * <p>注意：registry 的匹配用自研段级 Ant，单星号只匹配单段；对 engine 健康检查等
     * 路径与 Spring AntPathMatcher 结论一致。</p>
     */
    static final String[] EXCLUDE_PATTERNS =
            com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry
                    .clearanceExemptPatterns().toArray(new String[0]);

    public ClearanceMvcConfig(ClearanceInterceptor clearanceInterceptor) {
        this.clearanceInterceptor = clearanceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(clearanceInterceptor)
                .addPathPatterns(INCLUDE_PATTERNS)
                .excludePathPatterns(EXCLUDE_PATTERNS);
    }
}
