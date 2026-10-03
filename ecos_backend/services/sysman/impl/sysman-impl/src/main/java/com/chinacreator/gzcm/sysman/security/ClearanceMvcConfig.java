package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.security.registry.AnonymousEndpointRegistry;
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
     * W05（详细设计-00 C.3.2，M0）：豁免面不再内联 —— 一律由
     * {@link AnonymousEndpointRegistry#mvcExcludePatterns()} 单源生成
     * （ANONYMOUS + CLEARED_EXEMPT，双路径形态已随登记内置）。
     * 历史缺口（F-8：/api/* 变体缺失）已由 registry 双形态登记收口。
     */
    static final String[] EXCLUDE_PATTERNS =
            AnonymousEndpointRegistry.mvcExcludePatterns().toArray(String[]::new);

    public ClearanceMvcConfig(ClearanceInterceptor clearanceInterceptor) {
        this.clearanceInterceptor = clearanceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(clearanceInterceptor)
                .addPathPatterns(INCLUDE_PATTERNS)
                .excludePathPatterns(AnonymousEndpointRegistry.mvcExcludePatterns().toArray(String[]::new));
    }
}
