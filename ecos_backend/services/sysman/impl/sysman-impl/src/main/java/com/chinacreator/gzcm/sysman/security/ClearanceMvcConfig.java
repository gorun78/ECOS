package com.chinacreator.gzcm.sysman.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置 — 注册 ClearanceInterceptor。
 */
@Configuration
public class ClearanceMvcConfig implements WebMvcConfigurer {

    private final ClearanceInterceptor clearanceInterceptor;

    /**
     * H9-T2b：注册面必须覆盖双路径形态（既有 Controller 同时映射 {@code /api/v1/**} 与裸 {@code /api/**}）。
     * 只注册 {@code /api/v1/**} 会让裸路径整条链不进准入校验。
     */
    static final String[] INCLUDE_PATTERNS = {"/api/v1/**", "/api/**"};

    /**
     * H9-T2b：豁免面只留「匿名可达」类（auth + health，与 sysman SecurityConfig permitAll 同集合）。
     * {@code engine} 族豁免已收窄到 health 子路径；{@code knowledge/extract} 因实测存在
     * 无凭证的服务间调用（cognitive → kb）而暂留，待 H10 服务间凭证落地后收口。
     */
    static final String[] EXCLUDE_PATTERNS = {
            "/api/v1/auth/**", "/api/auth/**",
            "/api/v1/engine/*/health",
            "/api/v1/knowledge/health", "/api/knowledge/health",
            "/api/v1/knowledge/extract/**",
            "/api/health", "/health"
    };

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
